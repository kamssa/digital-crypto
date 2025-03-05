import { Injectable } from '@angular/core';
import { SearchResult } from './search-result.model';

@Injectable({
  providedIn: 'root',
})
export class SearchPGPKeyService {
  private mockData: SearchResult[] = [
    { id: 1, name: 'Document 1', date: new Date('2024-01-15'), expirationDate: new Date('2027-12-31'), apcode: 'AP001', isOwned: false, status: 'Active' },
    { id: 6, name: 'Document 1', date: new Date('2024-01-15'), expirationDate: new Date('2024-12-31'), apcode: 'AP001', isOwned: false, status: 'Active' },
    { id: 2, name: 'Report 2024', date: new Date('2024-01-15'), expirationDate: new Date('2024-12-31'), apcode: 'AP002', isOwned: false, status: 'Expired' }
  ];

  constructor() {}

  search(query: string): SearchResult[] {
    return this.mockData.filter(item =>
      item.id.toString().includes(query) ||
      item.name.toLowerCase().includes(query) ||
      item.apcode.toLowerCase().includes(query) ||
      item.status.toLowerCase().includes(query) ||
      this.formatDate(item.date).includes(query) ||
      this.formatDate(item.expirationDate).includes(query)
    );
  }

  private formatDate(date: Date): string {
    return date.toISOString().split('T')[0]; // Format YYYY-MM-DD
  }
}
import { Component, OnInit } from '@angular/core';
import { SearchPGPKeyService } from './search-pgp-key.service';
import { SearchResult } from './search-result.model';

@Component({
  selector: 'app-search',
  templateUrl: './search.component.html',
  styleUrls: ['./search.component.css'],
})
export class SearchComponent implements OnInit {
  searchTerm = '';
  results: SearchResult[] = [];
  totalRecords = 0;
  loading = true;

  constructor(private searchService: SearchPGPKeyService) {}

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.loading = true;
    setTimeout(() => {
      this.results = this.searchService.search(this.searchTerm);
      this.totalRecords = this.results.length;
      this.loading = false;
    }, 500); // Simule un délai API
  }

  onSearch() {
    this.loadData();
  }
}
<div>
  <input type="text" [(ngModel)]="searchTerm" (input)="onSearch()" pInputText placeholder="Rechercher..." />

  <p-table [value]="results" [paginator]="true" [rows]="5" [loading]="loading" [rowsPerPageOptions]="[5,10,20]"
           [sortable]="true" responsiveLayout="scroll">
    
    <ng-template pTemplate="header">
      <tr>
        <th pSortableColumn="id">ID <p-sortIcon field="id"></p-sortIcon></th>
        <th pSortableColumn="name">Nom <p-sortIcon field="name"></p-sortIcon></th>
        <th pSortableColumn="apcode">AP Code <p-sortIcon field="apcode"></p-sortIcon></th>
        <th pSortableColumn="date">Date <p-sortIcon field="date"></p-sortIcon></th>
        <th pSortableColumn="expirationDate">Expiration <p-sortIcon field="expirationDate"></p-sortIcon></th>
        <th pSortableColumn="status">Statut <p-sortIcon field="status"></p-sortIcon></th>
      </tr>
    </ng-template>

    <ng-template pTemplate="body" let-item>
      <tr>
        <td>{{ item.id }}</td>
        <td>{{ item.name }}</td>
        <td>{{ item.apcode }}</td>
        <td>{{ item.date | date:'yyyy-MM-dd' }}</td>
        <td>{{ item.expirationDate | date:'yyyy-MM-dd' }}</td>
        <td>{{ item.status }}</td>
      </tr>
    </ng-template>
  </p-table>
</div>
