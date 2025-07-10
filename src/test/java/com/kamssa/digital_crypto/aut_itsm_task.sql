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