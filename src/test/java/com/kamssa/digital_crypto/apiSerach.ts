import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SearchService {
  private apiUrl = 'https://mon-api.com/search'; // Remplace par ton URL API

  constructor(private http: HttpClient) {}

  search(query: string): Observable<Objet[]> {
    return this.http.get<Objet[]>(`${this.apiUrl}?q=${query}`);
  }
}



import { Component, OnInit } from '@angular/core';
import { SearchService } from './search.service';

@Component({
  selector: 'app-search',
  templateUrl: './search.component.html',
  styleUrls: ['./search.component.css']
})
export class SearchComponent implements OnInit {
  searchTerm: string = '';
  searchType: string = 'id'; // Valeur par défaut du dropdown
  results: Objet[] = []; // Résultats venant de l’API
  loading: boolean = false;

  constructor(private searchService: SearchService) {}

  ngOnInit() {
    this.loadAllData();
  }

  loadAllData() {
    this.loading = true;
    this.searchService.search('').subscribe(data => {
      this.results = data;
      this.loading = false;
    });
  }

  search() {
    this.loading = true;
    this.searchService.search(this.searchTerm).subscribe(data => {
      this.results = data;
      this.loading = false;
    });
  }
}





<div class="search-container">
    <div class="search-form">
        <input pInputText type="text" class="search-input"
               [(ngModel)]="searchTerm" placeholder="Search by ID, name..."
               (keyup.enter)="search()">
        
        <p-dropdown [options]="options" placeholder="Select an item"
                    [(ngModel)]="searchType">
        </p-dropdown>

        <p-button styleClass="addButton search-button pull-left" 
                  label="Search" (click)="search()">
        </p-button>
    </div>
</div>

<!-- Tableau PrimeNG -->
<p-table [value]="results" [paginator]="true" [rows]="5"
         [loading]="loading" [rowsPerPageOptions]="[5,10,20]"
         responsiveLayout="scroll">
    
    <ng-template pTemplate="header">
        <tr>
            <th pSortableColumn="id">ID <p-sortIcon field="id"></p-sortIcon></th>
            <th pSortableColumn="name">Nom <p-sortIcon field="name"></p-sortIcon></th>
            <th pSortableColumn="apcode">AP Code <p-sortIcon field="apcode"></p-sortIcon></th>
        </tr>
    </ng-template>

    <ng-template pTemplate="body" let-item>
        <tr>
            <td>{{ item.id }}</td>
            <td>{{ item.name }}</td>
            <td>{{ item.apcode }}</td>
        </tr>
    </ng-template>
</p-table>




options = [
  { label: 'ID', value: 'id' },
  { label: 'Name', value: 'name' },
  { label: 'AP Code', value: 'apcode' },
  { label: 'Date', value: 'date' }
];
