
import { Injectable } from '@angular/core';

interface SearchResult {
  id: number;
  name: string;
  date: string;
  expirationDate: string;
  apcode: string;
  isOwned: boolean;
  status: string;
}

@Injectable({
  providedIn: 'root',
})
export class SearchPGPKeyService {
  private mockData: SearchResult[] = [
    { id: 1, name: 'Document 1', date: '2024/01/15', expirationDate: '2027/12/31', apcode: 'AP001', isOwned: false, status: 'Active' },
    { id: 6, name: 'Document 1', date: '2024/01/15', expirationDate: '2024/12/31', apcode: 'AP001', isOwned: false, status: 'Active' },
    { id: 2, name: 'Report 2024', date: '2024-01-15', expirationDate: '2024-12-31', apcode: 'AP002', isOwned: false, status: 'Expired' }
  ];

  constructor() {}

  search(query: string, exactMatch: boolean = false): SearchResult[] {
    if (!query.trim()) return this.mockData;

    query = query.toLowerCase();
    return this.mockData.filter(item => {
      const idMatch = item.id.toString();
      const nameMatch = item.name.toLowerCase();
      const apcodeMatch = item.apcode.toLowerCase();
      const statusMatch = item.status.toLowerCase();
      const dateMatch = item.date;
      const expirationDateMatch = item.expirationDate;

      if (exactMatch) {
        return (
          idMatch === query ||
          nameMatch === query ||
          apcodeMatch === query ||
          statusMatch === query ||
          dateMatch === query ||
          expirationDateMatch === query
        );
      } else {
        return (
          idMatch.includes(query) ||
          nameMatch.includes(query) ||
          apcodeMatch.includes(query) ||
          statusMatch.includes(query) ||
          dateMatch.includes(query) ||
          expirationDateMatch.includes(query)
        );
      }
    });
  }
}

import { Component, OnInit } from '@angular/core';
import { SearchPGPKeyService } from './search-pgp-key.service';

interface SearchResult {
  id: number;
  name: string;
  date: string;
  expirationDate: string;
  apcode: string;
  isOwned: boolean;
  status: string;
}

@Component({
  selector: 'app-search',
  templateUrl: './search.component.html',
  styleUrls: ['./search.component.css'],
})
export class SearchComponent implements OnInit {
  searchTerm = '';
  results: SearchResult[] = [];
  exactMatch = false; // Par défaut, la recherche est en mode "inclusion"

  constructor(private searchService: SearchPGPKeyService) {}

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.results = this.searchService.search(this.searchTerm, this.exactMatch);
  }

  onSearch() {
    this.loadData();
  }

  toggleExactMatch() {
    this.exactMatch = !this.exactMatch;
    this.loadData();
  }
}

<div>
  <input type="text" [(ngModel)]="searchTerm" (input)="onSearch()" placeholder="Rechercher..." />

  <button (click)="toggleExactMatch()">
    Mode: {{ exactMatch ? 'Exact' : 'Inclusion' }}
  </button>

  <table border="1">
    <thead>
      <tr>
        <th>ID</th>
        <th>Nom</th>
        <th>AP Code</th>
        <th>Date</th>
        <th>Expiration</th>
        <th>Statut</th>
      </tr>
    </thead>
    <tbody>
      <tr *ngFor="let item of results">
        <td>{{ item.id }}</td>
        <td>{{ item.name }}</td>
        <td>{{ item.apcode }}</td>
        <td>{{ item.date }}</td>
        <td>{{ item.expirationDate }}</td>
        <td>{{ item.status }}</td>
      </tr>
    </tbody>
  </table>
</div>
