package com.pharmacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.inventory.BatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PharmacySystemTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("JWT_SECRET", () -> "test-secret-key-must-be-at-least-32-bytes");
        registry.add("pharmacy.admin.password", () -> "Admin12345");
        registry.add("pharmacy.admin.must-change-password", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BatchRepository batches;

    private String adminToken;

    @BeforeEach
    void loginAdmin() throws Exception {
        adminToken = token("admin", "Admin12345");
    }

    @Test
    void rejectsInvalidLoginAndProtectedEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/medicines"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cashierCannotManageUsers() throws Exception {
        createUser("cashier1", "CASHIER");
        String cashier = token("cashier1", "Cashier123");
        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + cashier))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/stock/adjustments")
                        .header("Authorization", "Bearer " + cashier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"batchId":1,"movementType":"ADJUSTED","direction":"OUT","quantity":1,"reason":"not allowed"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void purchaseSaleDispenseReturnAndReportStayConsistent() throws Exception {
        long storeId = location(null, "STORE", "STORE-A", "Store A");
        long shelfId = location(storeId, "SHELF", "A1", "Shelf A1");
        long rackId = location(shelfId, "RACK", "A1-R01", "Rack A1-R01");
        long positionId = location(rackId, "POSITION", "P01", "Position P01");
        long supplierId = supplier();
        long medicineId = medicine(positionId);
        long earlyBatchPurchase = purchase(supplierId, medicineId, positionId, "B-EARLY", LocalDate.now().plusMonths(2), 10, "4.00", "8.00");
        confirmPurchase(earlyBatchPurchase);
        long lateBatchPurchase = purchase(supplierId, medicineId, positionId, "B-LATE", LocalDate.now().plusMonths(8), 10, "4.00", "9.00");
        confirmPurchase(lateBatchPurchase);

        MvcResult stock = mockMvc.perform(get("/api/v1/medicines/" + medicineId + "/availability")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode availability = objectMapper.readTree(stock.getResponse().getContentAsString());
        assertThat(availability).hasSize(2);
        assertThat(availability.get(0).get("batchNumber").asText()).isEqualTo("B-EARLY");
        assertThat(availability.get(0).get("locationPath").asText()).contains("P01");

        MvcResult sale = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"medicineId":%d,"quantity":4}],
                                  "payments":[{"paymentMethodCode":"CASH","amount":32.00,"tenderedAmount":40.00}]
                                }
                                """.formatted(medicineId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(32.00))
                .andExpect(jsonPath("$.items[0].batchNumber").value("B-EARLY"))
                .andExpect(jsonPath("$.payments[0].changeAmount").value(8.00))
                .andReturn();
        long saleId = objectMapper.readTree(sale.getResponse().getContentAsString()).get("id").asLong();
        long saleItemId = objectMapper.readTree(sale.getResponse().getContentAsString()).get("items").get(0).get("id").asLong();

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"medicineId":%d,"quantity":100}],"payments":[{"paymentMethodCode":"CASH","amount":1.00}]}
                                """.formatted(medicineId)))
                .andExpect(status().isConflict());

        long customerId = customer();
        MvcResult prescription = mockMvc.perform(post("/api/v1/prescriptions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId":%d,
                                  "prescriptionDate":"%s",
                                  "prescriberName":"Dr. Amina",
                                  "items":[{"medicineId":%d,"dosage":"1 tablet","frequency":"twice daily","duration":"5 days","quantity":3}]
                                }
                                """.formatted(customerId, LocalDate.now(), medicineId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode rx = objectMapper.readTree(prescription.getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/prescriptions/" + rx.get("id").asLong() + "/dispense")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"prescriptionItemId":%d,"quantity":3}]}
                                """.formatted(rx.get("items").get(0).get("id").asLong())))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/prescriptions/" + rx.get("id").asLong() + "/review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/prescriptions/" + rx.get("id").asLong() + "/dispense")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"prescriptionItemId":%d,"quantity":3}]}
                                """.formatted(rx.get("items").get(0).get("id").asLong())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].batchNumber").value("B-EARLY"));

        mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"saleId":%d,"reason":"Customer changed their mind","refundMethodCode":"CASH","items":[{"saleItemId":%d,"quantity":1}]}
                                """.formatted(saleId, saleItemId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        int earlyOnHand = batches.findAll().stream()
                .filter(batch -> batch.getBatchNumber().equals("B-EARLY"))
                .findFirst().orElseThrow().getQuantityOnHand();
        assertThat(earlyOnHand).isEqualTo(4);

        mockMvc.perform(get("/api/v1/reports/sales/by-medicine")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].medicine").exists());
        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todaySales").exists());
        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").exists());
    }

    private String token(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void createUser(String username, String role) throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@pharmacy.test","fullName":"%s","password":"Cashier123","roles":["%s"]}
                                """.formatted(username, username, username, role)))
                .andExpect(status().isCreated());
    }

    private long location(Long parentId, String type, String code, String name) throws Exception {
        String parent = parentId == null ? "null" : parentId.toString();
        MvcResult result = mockMvc.perform(post("/api/v1/locations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId":%s,"locationType":"%s","code":"%s","name":"%s"}
                                """.formatted(parent, type, code, name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long supplier() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"City Wholesale","phone":"0700000000"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long medicine(long locationId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/medicines")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Paracetamol",
                                  "genericName":"Paracetamol",
                                  "dosageForm":"Tablet",
                                  "strength":"500 mg",
                                  "unit":"Tablet",
                                  "reorderLevel":5,
                                  "purchasePrice":4.00,
                                  "sellingPrice":8.00,
                                  "barcode":"PARA500",
                                  "locationId":%d
                                }
                                """.formatted(locationId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long purchase(long supplierId, long medicineId, long locationId, String batch, LocalDate expiry, int quantity, String purchasePrice, String sellingPrice) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId":%d,
                                  "purchaseDate":"%s",
                                  "items":[{
                                    "medicineId":%d,
                                    "quantity":%d,
                                    "purchasePrice":%s,
                                    "sellingPrice":%s,
                                    "batchNumber":"%s",
                                    "expiryDate":"%s",
                                    "locationId":%d
                                  }]
                                }
                                """.formatted(supplierId, LocalDate.now(), medicineId, quantity, purchasePrice, sellingPrice, batch, expiry, locationId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void confirmPurchase(long purchaseId) throws Exception {
        mockMvc.perform(post("/api/v1/purchases/" + purchaseId + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    private long customer() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Jane Patient","phone":"0711111111"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
