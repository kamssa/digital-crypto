CREATE TABLE AUTO_ITSM_TASK (
    -- Colonnes de l'entité
    id VARCHAR(255) NOT NULL,
    sys_id VARCHAR(255),
    assignment_group VARCHAR(255),
    status VARCHAR(255),
    link VARCHAR(255),
    api_link VARCHAR(255),
    creation_date DATE,
    last_updated DATE,
    first_priority_set_by_certis INT,
    last_priority_set_by_certis INT,
    inc_type VARCHAR(255),
    automation_hub_id VARCHAR(255),
    parent_incident VARCHAR(255),

    -- Définition des contraintes (Clé primaire et clé étrangère)
    CONSTRAINT pk_auto_itsm_task PRIMARY KEY (id),
    CONSTRAINT fk_auto_itsm_task_parent 
        FOREIGN KEY (parent_incident) 
        REFERENCES AUTO_ITSM_TASK (id)
);
-- =======================================================================================
-- SCRIPT DE MODIFICATION DE LA TABLE SAN (Version 2 : AJOUT de colonnes)
-- À exécuter en une seule fois (ou commande par commande)
-- =======================================================================================

-- COMMANDE 1 : AJOUTE les nouvelles colonnes.
ALTER TABLE OWN_19382_COP.SAN
ADD (
    type       VARCHAR2(20),
    san_value  VARCHAR2(100)
);

-- COMMANDE 2 : MET À JOUR les données pour les lignes existantes.
UPDATE OWN_19382_COP.SAN
SET
    type = 'DNSNAME',
    san_value = url;

-- COMMANDE 3 : MODIFIE les colonnes pour les rendre non nulles.
ALTER TABLE OWN_19382_COP.SAN
MODIFY (
    type      NOT NULL,
    san_value NOT NULL
);

-- COMMANDE 4 (Optionnelle) : AJOUTE une règle de validation.
ALTER TABLE OWN_19382_COP.SAN
ADD CONSTRAINT san_type_chk CHECK (type IN ('DNSNAME', 'RFC822NAME', 'IPADDRESS', 'OTHERNAME_GUID', 'OTHERNAME_UPN', 'URI'));

-- COMMANDE 5 : SAUVEGARDE les changements de façon permanente.
COMMIT;