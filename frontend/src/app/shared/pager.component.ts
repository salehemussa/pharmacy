import { Component, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-pager',
  imports: [ButtonModule],
  template: `
    <div class="pager">
      <span>{{ total() }} records</span>
      <span class="actions" style="margin:0">
        <p-button label="Previous" [text]="true" [disabled]="page() <= 0" (onClick)="pageChange.emit(page() - 1)" />
        <span>Page {{ totalPages() === 0 ? 0 : page() + 1 }} of {{ totalPages() }}</span>
        <p-button label="Next" [text]="true" [disabled]="page() + 1 >= totalPages()" (onClick)="pageChange.emit(page() + 1)" />
      </span>
    </div>
  `
})
export class PagerComponent {
  page = input(0);
  totalPages = input(0);
  total = input(0);
  pageChange = output<number>();
}
