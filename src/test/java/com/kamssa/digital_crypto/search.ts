interface Objet {
  id: string;
  name: string;
  apcode: string;
  date: Date;  // Ajout du champ date
}

let objets: Objet[] = [
  { id: 'id_1', name: 'name_1', apcode: 'apcode_1', date: new Date('2024-01-01') },
  { id: 'id_2', name: 'name_2', apcode: 'apcode_2', date: new Date('2024-02-15') },
  { id: 'id_3', name: 'name_3', apcode: 'apcode_3', date: new Date('2024-03-10') }
];

@Injectable()
export class SearchPGPGPGKeyService {
  private items: Objet[] = objets;  // Stocke la liste des objets

  constructor() { }

  addItem(item: Objet) {
    this.items.push(item);
  }

  removeItem(index: number) {
    if (index > -1 && index < this.items.length) {
      this.items.splice(index, 1);
    }
  }

  getItems(): Objet[] {
    return this.items;
  }

  search(query: string): Objet[] {
    if (!query.trim()) return this.items;

    query = query.toLowerCase();
    return this.items.filter(item =>
      item.id.toLowerCase().includes(query) ||
      item.name.toLowerCase().includes(query) ||
      item.apcode.toLowerCase().includes(query) ||
      item.date.toISOString().includes(query) // Recherche aussi par date
    );
  }
}
example ds un composant angular
searchResults: Objet[] = [];

search(query: string) {
  this.searchResults = this.searchPGPKeyService.search(query);
}
Puis dans le HTML :

html
Copier
Modifier
<input type="text" (input)="search($event.target.value)" placeholder="Rechercher...">
<p-table [value]="searchResults"></p-table>