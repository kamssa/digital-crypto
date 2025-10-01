Absolument. Voici le récapitulatif final et consolidé de tous les fichiers et extraits de code que nous avons modifiés pour résoudre ce ticket, du backend au frontend.
Partie 1 : Backend (Java / Spring Boot)
Fichier 1 : San.java
(Ajout de equals et hashCode pour la déduplication)
code
Java
// Dans la classe 'San.java'
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    San san = (San) o;
    if (getType() != san.getType()) return false;
    if (getSanValue() == null) {
        return san.getSanValue() == null;
    }
    return getSanValue().equalsIgnoreCase(san.getSanValue());
}

@Override
public int hashCode() {
    String lowerCaseSanValue = (getSanValue() == null) ? null : getSanValue().toLowerCase();
    return Objects.hash(getType(), lowerCaseSanValue);
}
Fichier 2 : CertificateCsrDecoder.java
(Modification de la méthode pour qu'elle retourne une List<San> et gère tous les types)
code
Java
// Nouveaux imports nécessaires
import com.bnpparibas.certis.certificate.request.model.San;
import com.bnpparibas.certis.certificate.request.model.SanTypeEnum;
import org.bouncycastle.asn1.pkcs.Attribute;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERTaggedObject;

// Remplacez votre méthode getSansList (ou créez-en une nouvelle)
public List<San> getSansList(String decodedCSR) throws IOException, FailedToParseCsrException {
    if (decodedCSR == null || StringUtils.isEmpty(decodedCSR)) {
        return Collections.emptyList();
    }
    PKCS10CertificationRequest csr = csrPemToPKCS10(decodedCSR);
    assert csr != null;

    try {
        Attribute[] attrs = csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest);
        if (attrs.length == 0) {
            return Collections.emptyList();
        }

        ASN1Encodable[] values = attrs[0].getAttributeValues();
        Extension extn = Extensions.getInstance(values[0]).getExtension(Extension.subjectAlternativeName);
        if (extn == null) {
            return Collections.emptyList();
        }
        
        List<San> sanList = new ArrayList<>();
        GeneralNames generalNames = GeneralNames.getInstance(extn.getParsedValue());

        for (GeneralName name : generalNames.getNames()) {
            San sanEntity = new San();
            switch (name.getTagNo()) {
                case GeneralName.dNSName:
                    sanEntity.setType(SanTypeEnum.DNSNAME);
                    sanEntity.setSanValue(name.getName().toString());
                    sanList.add(sanEntity);
                    break;
                case GeneralName.iPAddress:
                    sanEntity.setType(SanTypeEnum.IPADDRESS);
                    sanEntity.setSanValue(name.getName().toString());
                    sanList.add(sanEntity);
                    break;
                case GeneralName.rfc822Name:
                    sanEntity.setType(SanTypeEnum.RFC822NAME);
                    sanEntity.setSanValue(name.getName().toString());
                    sanList.add(sanEntity);
                    break;
                case GeneralName.uniformResourceIdentifier:
                    sanEntity.setType(SanTypeEnum.URI);
                    sanEntity.setSanValue(name.getName().toString());
                    sanList.add(sanEntity);
                    break;
                case GeneralName.otherName:
                    ASN1Sequence otherNameSequence = ASN1Sequence.getInstance(name.getName());
                    ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) otherNameSequence.getObjectAt(0);
                    if (otherNameSequence.size() > 1) {
                        ASN1Encodable valueEncodable = ((DERTaggedObject) otherNameSequence.getObjectAt(1)).getObject();
                        String otherNameValue = valueEncodable.toString();
                        final String upnOid = "1.3.6.1.4.1.311.20.2.3";
                        final String guidOid = "1.3.6.1.4.1.311.25.1";
                        if (upnOid.equals(oid.getId())) {
                            sanEntity.setType(SanTypeEnum.OTHERNAME_UPN);
                            sanEntity.setSanValue(otherNameValue);
                            sanList.add(sanEntity);
                        } else if (guidOid.equals(oid.getId())) {
                            sanEntity.setType(SanTypeEnum.OTHERNAME_GUID);
                            sanEntity.setSanValue(otherNameValue);
                            sanList.add(sanEntity);
                        }
                    }
                    break;
            }
        }
        return sanList;
    } catch (Exception e) {
        throw new FailedToParseCsrException(e.getMessage(), e.getCause());
    }
}
Fichier 3 : RequestServiceImpl.java
(Refonte de createRequest pour la fusion et la liaison des entités)
code
Java
// Nouveaux imports nécessaires
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
// ...

@Override
public RequestDto createRequest(RequestDto requestDto) {
    List<San> finalSanList = new ArrayList<>();

    if (requestDto.getCertificate() != null) {
        List<San> sansFromInput = new ArrayList<>();
        if (requestDto.getCertificate().getSans() != null) {
            sansFromInput.addAll(requestDto.getCertificate().getSans());
        }

        List<San> sansFromCsr = new ArrayList<>();
        final String csr = this.fileManagerService.extractCsr(requestDto, Boolean.TRUE);
        if (!StringUtils.isEmpty(csr)) {
            try {
                sansFromCsr = this.certificateCsrDecoder.getSansList(csr);
            } catch (Exception e) {
                LOGGER.error("Message de CSR create:" + e.getMessage());
                throw new CertisRequestException("error.request.csr.invalid_format", HttpStatus.BAD_REQUEST);
            }
        }

        Set<San> finalUniqueSans = new LinkedHashSet<>();
        finalUniqueSans.addAll(sansFromInput);
        finalUniqueSans.addAll(sansFromCsr);
        
        finalSanList.addAll(finalUniqueSans);
    }
    
    if (requestDto.getComment() != null && requestDto.getComment().length() > 3999) {
        requestDto.setComment(requestDto.getComment().substring(0, 3998));
    }
    
    Request request = dtoToEntity(requestDto);
    
    if (request.getCertificate() != null) {
        request.getCertificate().getSans().clear();
        request.getCertificate().getSans().addAll(finalSanList);

        for (San san : request.getCertificate().getSans()) {
            san.setCertificate(request.getCertificate());
        }
    }

    if (!CollectionUtils.isEmpty(request.getContacts())) {
        for (Contact cont : request.getContacts()) {
            cont.setRequests(request);
        }
    }
    
    RequestDto requestDtoResult = entityToDto(requestDao.save(request));
    
    return requestDtoResult;
}
Partie 2 : Frontend (Angular)
Fichier 1 : utils.ts
(Centralisation de toutes les configurations)
code
TypeScript
// enum numérique
export enum SanType {
  DNSNAME,
  RFC822NAME,
  IPADDRESS,
  URI,
  OTHERNAME_GUID,
  OTHERNAME_UPN,
}

// Mapper pour les classes CSS des badges
export const styleMapper = {
  [SanType.DNSNAME]: 'badge-dnsname',
  [SanType.RFC822NAME]: 'badge-rfc822name',
  [SanType.IPADDRESS]: 'badge-ipaddress',
  [SanType.URI]: 'badge-uri',
  [SanType.OTHERNAME_GUID]: 'badge-othername',
  [SanType.OTHERNAME_UPN]: 'badge-othername_upn',
};

// Expressions régulières pour la validation
export const SANS_REGEX_PATTERNS = {
  [SanType.DNSNAME]: /^[a-zA-Z0-9\.\-\*_]+$/,
  [SanType.RFC822NAME]: /^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$/,
  [SanType.IPADDRESS]: /^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$/,
  [SanType.OTHERNAME_GUID]: /^[a-fA-F0-9]{32}$/,
  [SanType.OTHERNAME_UPN]: /^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$/,
  [SanType.URI]: /^(https?|ldaps?|ftp|file|tag|urn|data|tel):[a-zA-Z0-9+&@#/%?=~_!:,.;]*[a-zA-Z0-9+&@#/%=~_]/i
};

// Tableau d'objets pour le dropdown PrimeNG
export const SAN_TYPE_OPTIONS = [
  { label: 'DNS Name', value: SanType.DNSNAME, styleClass: styleMapper[SanType.DNSNAME] },
  { label: 'E-mail', value: SanType.RFC822NAME, styleClass: styleMapper[SanType.RFC822NAME] },
  { label: 'Adresse IP', value: SanType.IPADDRESS, styleClass: styleMapper[SanType.IPADDRESS] },
  { label: 'URI', value: SanType.URI, styleClass: styleMapper[SanType.URI] },
  { label: 'GUID', value: SanType.OTHERNAME_GUID, styleClass: styleMapper[SanType.OTHERNAME_GUID] },
  { label: 'UPN', value: SanType.OTHERNAME_UPN, styleClass: styleMapper[SanType.OTHERNAME_UPN] }
];

// Fonction utilitaire pour la traduction
export function getSanTypeName(type: SanType): string {
  return SanType[type];
}
Fichier 2 : request-detail-section.component.ts
(Adaptation du composant pour utiliser les nouvelles configurations)
code
TypeScript
// Imports nécessaires
import { FormArray, ... } from '@angular/forms';
import { SAN_TYPE_OPTIONS, getSanTypeName } from 'src/app/shared/utils';

export class RequestDetailSectionComponent {

  // Propriété pour alimenter le dropdown
  public sanTypes = SAN_TYPE_OPTIONS;
  // Propriété pour la clé de traduction
  public getSanTypeName = getSanTypeName;

  // Méthode pour caster le FormArray (corrige l'erreur de compilation)
  getSansControls(): FormArray {
    return this.requestDetailSectionForm.get('sans') as FormArray;
  }
  
  // La logique de validation dynamique dans createSanGroup reste inchangée et est correcte.
  // ...
}
Fichier 3 : request-detail-section.component.html
(Adaptation du template pour le dropdown avancé et la traduction)
code
Html
<!-- Dans la boucle *ngFor="... let sanGroup of getSansControls().controls ..." -->
<div class="san-type-column">
  <p-dropdown 
      [options]="sanTypes" 
      formControlName="type"
      optionLabel="label"   
      optionValue="value"
      [style]="{'width': '100%'}">
      
    <ng-template let-selectedItem pTemplate="selectedItem">
        <div *ngIf="selectedItem">
            <p-badge [value]="selectedItem.label" [styleClass]="selectedItem.styleClass"></p-badge>
        </div>
    </ng-template>
    
    <ng-template let-item pTemplate="item">
        <p-badge [value]="item.label" [styleClass]="item.styleClass"></p-badge>
    </ng-template>
  </p-dropdown>
</div>

<!-- Ligne de l'erreur de traduction corrigée -->
<small *ngIf="sanGroup.get('value')?.invalid && (sanGroup.get('value')?.dirty || sanGroup.get('value')?.touched)">
  <small class="p-error" *ngIf="sanGroup.get('value').errors?.pattern">
    {{ 'requestDetailsSection.errors.sanFormat.' + getSanTypeName(sanGroup.get('type').value) | translate }}
  </small>
</small>