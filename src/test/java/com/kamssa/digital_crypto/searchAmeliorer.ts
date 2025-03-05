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

  search(query: string, sortBy: keyof SearchResult = 'id', sortOrder: 'asc' | 'desc' = 'asc', page: number = 1, pageSize: number = 5): SearchResult[] {
    let results = this.mockData.filter(item =>
      item.id.toString().includes(query) ||
      item.name.toLowerCase().includes(query) ||
      item.apcode.toLowerCase().includes(query) ||
      item.status.toLowerCase().includes(query) ||
      this.formatDate(item.date).includes(query) ||
      this.formatDate(item.expirationDate).includes(query)
    );

    results = this.sortResults(results, sortBy, sortOrder);
    return this.paginateResults(results, page, pageSize);
  }

  private sortResults(results: SearchResult[], sortBy: keyof SearchResult, sortOrder: 'asc' | 'desc'): SearchResult[] {
    return results.sort((a, b) => {
      const valueA = typeof a[sortBy] === 'string' ? (a[sortBy] as string).toLowerCase() : a[sortBy];
      const valueB = typeof b[sortBy] === 'string' ? (b[sortBy] as string).toLowerCase() : b[sortBy];

      if (valueA < valueB) return sortOrder === 'asc' ? -1 : 1;
      if (valueA > valueB) return sortOrder === 'asc' ? 1 : -1;
      return 0;
    });
  }

  private paginateResults(results: SearchResult[], page: number, pageSize: number): SearchResult[] {
    const start = (page - 1) * pageSize;
    return results.slice(start, start + pageSize);
  }

  private formatDate(date: Date): string {
    return date.toISOString().split('T')[0]; // Format YYYY-MM-DD
  }
}
import { Component } from '@angular/core';
import { SearchPGPKeyService } from './search-pgp-key.service';
import { SearchResult } from './search-result.model';

@Component({
  selector: 'app-search',
  templateUrl: './search.component.html',
  styleUrls: ['./search.component.css'],
})
export class SearchComponent {
  searchTerm = '';
  results: SearchResult[] = [];
  sortBy: keyof SearchResult = 'id';
  sortOrder: 'asc' | 'desc' = 'asc';
  currentPage = 1;
  pageSize = 5;
  totalResults = 0;

  constructor(private searchService: SearchPGPKeyService) {
    this.updateResults();
  }

  updateResults() {
    const allResults = this.searchService.search(this.searchTerm, this.sortBy, this.sortOrder);
    this.totalResults = allResults.length;
    this.results = this.searchService.search(this.searchTerm, this.sortBy, this.sortOrder, this.currentPage, this.pageSize);
  }

  onSearch() {
    this.currentPage = 1;
    this.updateResults();
  }

  onSort(field: keyof SearchResult) {
    this.sortBy = field;
    this.sortOrder = this.sortOrder === 'asc' ? 'desc' : 'asc';
    this.updateResults();
  }

  goToPage(page: number) {
    if (page >= 1 && page <= Math.ceil(this.totalResults / this.pageSize)) {
      this.currentPage = page;
      this.updateResults();
    }
  }
}
<div>
  <input type="text" [(ngModel)]="searchTerm" (input)="onSearch()" placeholder="Rechercher..." />

  <table>
    <thead>
      <tr>
        <th (click)="onSort('id')">ID</th>
        <th (click)="onSort('name')">Nom</th>
        <th (click)="onSort('apcode')">AP Code</th>
        <th (click)="onSort('date')">Date</th>
        <th (click)="onSort('expirationDate')">Expiration</th>
        <th (click)="onSort('status')">Statut</th>
      </tr>
    </thead>
    <tbody>
      <tr *ngFor="let item of results">
        <td>{{ item.id }}</td>
        <td>{{ item.name }}</td>
        <td>{{ item.apcode }}</td>
        <td>{{ item.date | date:'yyyy-MM-dd' }}</td>
        <td>{{ item.expirationDate | date:'yyyy-MM-dd' }}</td>
        <td>{{ item.status }}</td>
      </tr>
    </tbody>
  </table>

  <div class="pagination">
    <button (click)="goToPage(currentPage - 1)" [disabled]="currentPage === 1">Précédent</button>
    <span>Page {{ currentPage }} / {{ Math.ceil(totalResults / pageSize) }}</span>
    <button (click)="goToPage(currentPage + 1)" [disabled]="currentPage === Math.ceil(totalResults / pageSize)">Suivant</button>
  </div>
</div>
