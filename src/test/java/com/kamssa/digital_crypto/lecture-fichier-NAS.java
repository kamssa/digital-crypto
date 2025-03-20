public String getPgpCertIdFromAscFile(String requestId) {
    File nasDir = new File("/chemin/vers/le/NAS/" + requestId); // Remplace par le chemin réel

    File[] ascFiles = nasDir.listFiles((dir, name) -> name.equalsIgnoreCase("pgpCertID.asc"));
    if (ascFiles == null || ascFiles.length == 0) {
        LOGGER.warn("Fichier pgpCertID.asc non trouvé pour l'ID: {}", requestId);
        return null;
    }

    File pgpCertFile = ascFiles[0];
    StringBuilder contentBuilder = new StringBuilder();

    try (BufferedReader reader = new BufferedReader(new FileReader(pgpCertFile))) {
        String line;
        while ((line = reader.readLine()) != null) {
            contentBuilder.append(line).append("\n");
        }
    } catch (IOException e) {
        LOGGER.error("Erreur lors de la lecture de pgpCertID.asc: {}", e.getMessage(), e);
        return null;
    }

    return contentBuilder.toString().trim(); // retourne le contenu brut du fichier asc
}
private boolean isPgpCertIDInNas(String pgpCertID) {
    // Crée le chemin vers le dossier correspondant à l'ID
    Path directoryPath = Paths.get(NAS_PATH, pgpCertID);
    
    // Crée le chemin vers le fichier "pgpCertID.asc" dans ce dossier
    Path filePath = directoryPath.resolve("pgpCertID.asc");
    
    // Vérifie si le fichier existe et est lisible
    return Files.exists(filePath) && Files.isReadable(filePath);
}
