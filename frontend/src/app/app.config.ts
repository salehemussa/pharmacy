import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { DialogService } from 'primeng/dynamicdialog';
import { definePreset } from '@primeng/themes';
import Aura from '@primeng/themes/aura';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';

const Pharmacy = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{emerald.50}',
      100: '{emerald.100}',
      200: '{emerald.200}',
      300: '{emerald.300}',
      400: '{emerald.400}',
      500: '{emerald.600}',
      600: '{emerald.700}',
      700: '{emerald.800}',
      800: '{emerald.900}',
      900: '{emerald.950}',
      950: '{emerald.950}'
    }
  }
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
    providePrimeNG({
      theme: {
        preset: Pharmacy,
        options: { darkModeSelector: false }
      }
    }),
    MessageService,
    DialogService
  ]
};
