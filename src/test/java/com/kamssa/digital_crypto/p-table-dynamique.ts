<p-table [value]="tableData" [paginator]="true" [rows]="10">
  <ng-template pTemplate="header">
    <tr>
      <th *ngFor="let col of columns">{{ col.header }}</th>
    </tr>
  </ng-template>

  <ng-template pTemplate="body" let-row>
    <tr>
      <td *ngFor="let col of columns" [ngSwitch]="col.type">
        
        <!-- Statut du certificat -->
        <ng-container *ngSwitchCase="'CERTIFICATE_STATUS'">
          <span class="badge" [ngClass]="getStyleByStatus(row[col.field])">
            {{ 'certificateStatus.' + row[col.field] | translate }}
          </span>
        </ng-container>

        <!-- Code AP -->
        <ng-container *ngSwitchCase="'CODE_AP'">
          {{ row[col.field] }}
        </ng-container>

        <!-- Date d'expiration -->
        <ng-container *ngSwitchCase="'EXPIRATION_DATE'">
          {{ row[col.field] | date: 'dd/MM/yyyy' }}
        </ng-container>

        <!-- Propriétaire -->
        <ng-container *ngSwitchCase="'OWNER'">
          {{ row[col.field] }}
        </ng-container>

        <!-- Affichage par défaut -->
        <ng-container *ngSwitchDefault>
          {{ row[col.field] }}
        </ng-container>

      </td>
    </tr>
  </ng-template>
</p-table>
import { Component } from '@angular/core';

@Component({
  selector: 'app-my-table',
  templateUrl: './my-table.component.html',
  styleUrls: ['./my-table.component.css']
})
export class MyTableComponent {
  // Définition des colonnes dynamiques
  columns = [
    { field: 'status', header: 'Statut', type: 'CERTIFICATE_STATUS' },
    { field: 'codeAp', header: 'Code AP', type: 'CODE_AP' },
    { field: 'expirationDate', header: 'Date Expiration', type: 'EXPIRATION_DATE' },
    { field: 'owner', header: 'Propriétaire', type: 'OWNER' }
  ];

  // Données d'exemple
  tableData = [
    { id: 1, status: 'VALID', codeAp: 'AP-1234', expirationDate: '2025-06-15', owner: 'Alice' },
    { id: 2, status: 'EXPIRED', codeAp: 'AP-5678', expirationDate: '2024-03-10', owner: 'Bob' },
    { id: 3, status: 'REVOKED', codeAp: 'AP-9101', expirationDate: '2023-12-01', owner: 'Charlie' }
  ];

  // Exemple de méthode pour styliser une cellule selon le statut
  getStyleByStatus(status: string) {
    return status === 'VALID' ? 'badge-success' : status === 'EXPIRED' ? 'badge-warning' : 'badge-danger';
  }
}
