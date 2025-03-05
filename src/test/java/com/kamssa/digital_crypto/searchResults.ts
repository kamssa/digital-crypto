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

@Injectable()
export class SearchPGPKeyService {
  private mockData: SearchResult[] = [
    { id: 1, name: 'Document 1', date: '2024/01/15', expirationDate: '31/12/2027', apcode: 'AP001', isOwned: false, status: 'Active' },
    { id: 6, name: 'Document 1', date: '2024/01/15', expirationDate: '31/12/2024', apcode: 'AP001', isOwned: false, status: 'Active' },
    { id: 2, name: 'Report 2024', date: '2024-01-15', expirationDate: '2024-12-31', apcode: 'AP002', isOwned: false, status: 'Expired' }
  ];

  constructor() { }

  search(query: string): SearchResult[] {
    if (!query.trim()) return this.mockData;

    query = query.toLowerCase();
    return this.mockData.filter(item =>
      item.id.toString().includes(query) ||
      item.name.toLowerCase().includes(query) ||
      item.apcode.toLowerCase().includes(query) ||
      item.status.toLowerCase().includes(query) ||
      item.date.includes(query) ||
      item.expirationDate.includes(query)
    );
  }
}
Utilisation dans un composant Angular :
Dans key-search.component.ts :

typescript
Copier
Modifier
searchResults: SearchResult[] = [];

constructor(private searchService: SearchPGPKeyService) {}

search(query: string) {
  this.searchResults = this.searchService.search(query);
}
Dans le HTML :

html
Copier
Modifier
<input type="text" (input)="search($event.target.value)" placeholder="Rechercher...">
<ul>
  <li *ngFor="let result of searchResults">
    {{ result.name }} - {{ result.apcode }} - {{ result.status }}
  </li>
</ul>