
Model
ThinkingThoughts
(experimental)
Auto
Expand to view model thoughts

chevron_right
Absolument ! Voici un guide complet et détaillé pour créer un script Bash qui orchestre le lancement de votre front-end Angular et de votre back-end Spring Boot, tout en les intégrant avec Dynatrace.
Le script ne "connecte" pas le code (c'est le rôle de vos appels HTTP dans Angular et de vos contrôleurs dans Spring), mais il lance les deux applications de manière coordonnée et s'assure qu'elles sont correctement instrumentées par Dynatrace.
Comprendre le Fonctionnement de Dynatrace
Avant d'écrire le script, il est crucial de comprendre comment Dynatrace fonctionne avec ces technologies :
Pour Spring Boot (Back-end Java) : Dynatrace utilise le OneAgent. Il s'agit d'un agent qui doit être installé sur la machine (serveur, VM, conteneur). Lorsque vous lancez votre application Java, vous devez attacher cet agent au processus JVM. Il instrumentera alors automatiquement votre code, suivra les transactions, les appels à la base de données, etc.
Pour Angular (Front-end JavaScript) : Dynatrace utilise le Real User Monitoring (RUM). Le OneAgent, qui surveille votre serveur back-end, injecte automatiquement un petit tag JavaScript dans le code HTML de votre application. Ce tag collecte les métriques de performance directement depuis le navigateur de l'utilisateur (temps de chargement, erreurs JavaScript, actions utilisateur, etc.) et les relie aux transactions back-end correspondantes.
Le script va donc se concentrer sur :
Lancer le back-end Spring Boot en y attachant le OneAgent.
Lancer le front-end Angular (généralement avec ng serve pour le développement).
Prérequis
Dynatrace OneAgent installé : Le OneAgent doit être installé sur la machine où vous allez exécuter le script. En général, sous Linux, il se trouve dans /opt/dynatrace/oneagent.
Projets Angular et Spring Boot : Vos projets doivent être fonctionnels séparément.
Outils de build installés :
Node.js et Angular CLI (ng) pour le front-end.
JDK et Maven (mvn) ou Gradle (gradlew) pour le back-end.
Configuration du Proxy Angular : Pour éviter les problèmes de CORS en développement, il est fortement recommandé d'utiliser un proxy. Créez un fichier proxy.conf.json à la racine de votre projet Angular :
code
JSON
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "logLevel": "debug"
  }
}
target: L'URL de votre back-end Spring Boot.
/api: Le préfixe de toutes vos requêtes API. Adaptez-le si nécessaire.
Étape 1 : Le Script Bash d'Orchestration
Voici un script complet et commenté. Copiez-le dans un fichier que vous nommerez par exemple start_app_with_dynatrace.sh.
code
Bash
#!/bin/bash

# ==============================================================================
# Script pour démarrer une application Angular + Spring Boot avec Dynatrace
# ==============================================================================

# Arrête le script immédiatement si une commande échoue
set -e

# --- Configuration (À MODIFIER SELON VOTRE PROJET) ---

# Chemins vers vos projets
FRONT_DIR="/chemin/vers/votre/projet/angular"
BACK_DIR="/chemin/vers/votre/projet/spring-boot"

# Chemin vers l'agent Java de Dynatrace OneAgent
# Sur Linux, il est souvent à cet endroit. Vérifiez sur votre système.
DYNATRACE_AGENT_PATH="/opt/dynatrace/oneagent/agent/lib64/oneagentjava.jar"

# Nom du fichier JAR de votre application Spring Boot (après le build)
# Il se trouve généralement dans le dossier 'target'
BACKEND_JAR_NAME="votre-application-0.0.1-SNAPSHOT.jar"

# Port du backend
BACKEND_PORT=8080

# --- Fonctions ---

# Fonction pour nettoyer les processus en arrière-plan à la sortie du script
cleanup() {
    echo ""
    echo "----------------------------------------------------"
    echo "Arrêt des applications..."
    echo "----------------------------------------------------"
    # 'kill 0' envoie le signal TERM à tous les processus du groupe de processus du script
    # Cela arrêtera les processus lancés en arrière-plan (ng serve, java)
    kill 0
    exit
}

# Intercepte le signal de fin (CTRL+C) pour appeler la fonction de nettoyage
trap cleanup INT TERM

# --- Exécution ---

echo "----------------------------------------------------"
echo "Vérification des prérequis..."
echo "----------------------------------------------------"

if [ ! -f "$DYNATRACE_AGENT_PATH" ]; then
    echo "ERREUR : L'agent Dynatrace n'a pas été trouvé à l'emplacement : $DYNATRACE_AGENT_PATH"
    echo "Veuillez vérifier que le OneAgent est installé et que le chemin est correct."
    exit 1
fi

echo "Agent Dynatrace trouvé."

# 1. Builder le projet Backend (Spring Boot)
echo "----------------------------------------------------"
echo "Build du Backend (Spring Boot)..."
echo "----------------------------------------------------"
cd "$BACK_DIR"
#./mvnw clean package -DskipTests # Utilisez ./mvnw si vous l'avez
mvn clean package -DskipTests     # Ou 'mvn' si vous l'avez installé globalement
# Pour Gradle : ./gradlew build

# Chemin complet vers le JAR
BACKEND_JAR_PATH="$BACK_DIR/target/$BACKEND_JAR_NAME"

if [ ! -f "$BACKEND_JAR_PATH" ]; then
    echo "ERREUR : Le fichier JAR du backend n'a pas été trouvé : $BACKEND_JAR_PATH"
    exit 1
fi

echo "Build du Backend terminé."

# 2. Démarrer le Backend avec l'agent Dynatrace
echo "----------------------------------------------------"
echo "Démarrage du Backend sur le port $BACKEND_PORT AVEC Dynatrace..."
echo "----------------------------------------------------"
java -javaagent:"$DYNATRACE_AGENT_PATH" -jar "$BACKEND_JAR_PATH" &
BACKEND_PID=$!
echo "Backend démarré avec le PID : $BACKEND_PID"

# Petite pause pour laisser le temps au backend de démarrer
sleep 15

# 3. Démarrer le Frontend (Angular)
echo "----------------------------------------------------"
echo "Démarrage du Frontend (Angular)..."
echo "----------------------------------------------------"
cd "$FRONT_DIR"
# La commande '--proxy-config' est essentielle pour éviter les problèmes de CORS
ng serve --proxy-config proxy.conf.json --open &
FRONTEND_PID=$!
echo "Frontend démarré avec le PID : $FRONTEND_PID"


# --- Attente ---
echo ""
echo "======================================================================"
echo "L'application est en cours d'exécution."
echo "Frontend : http://localhost:4200"
echo "Backend  : http://localhost:8080"
echo "Appuyez sur CTRL+C pour tout arrêter."
echo "======================================================================"

# Attend que les processus en arrière-plan se terminent (ce qui n'arrivera que
# si l'un d'eux plante ou si l'utilisateur appuie sur CTRL+C)
wait $BACKEND_PID $FRONTEND_PID
Étape 2 : Comment Utiliser le Script
Personnaliser le Script :
Ouvrez le fichier start_app_with_dynatrace.sh.
Modifiez les variables dans la section --- Configuration --- pour qu'elles correspondent aux chemins de vos projets et au nom de votre fichier JAR.
Vérifiez que le chemin DYNATRACE_AGENT_PATH est correct pour votre installation.
Rendre le Script Exécutable :
Ouvrez un terminal et naviguez jusqu'au répertoire où vous avez enregistré le script, puis exécutez la commande suivante :
code
Sh
chmod +x start_app_with_dynatrace.sh
Lancer le Script :
Exécutez simplement le script :
code
Sh
./start_app_with_dynatrace.sh
Que va-t-il se passer ?
Le script vérifiera la présence de l'agent Dynatrace.
Il va builder votre application Spring Boot en utilisant Maven (ou Gradle si vous modifiez la commande).
Il lancera le JAR de Spring Boot en attachant l'agent Dynatrace via le paramètre -javaagent. C'est l'étape clé pour le monitoring du back-end.
Il attendra quelques secondes pour que le back-end soit prêt.
Il lancera le serveur de développement Angular (ng serve) en utilisant votre proxy.conf.json pour rediriger les appels API vers le back-end.
Votre navigateur par défaut s'ouvrira sur http://localhost:4200.
Les deux processus (Java et Node) tourneront en arrière-plan. Lorsque vous appuierez sur CTRL+C dans le terminal, la fonction cleanup sera appelée pour arrêter proprement les deux applications.
Étape 3 : Vérification dans Dynatrace
Connectez-vous à votre interface Dynatrace.
Vérifiez le back-end :
Allez dans la section "Transactions and services". Vous devriez voir un nouveau service Java apparaître, correspondant à votre application Spring Boot.
Cliquez dessus pour voir les transactions, les temps de réponse, et les éventuelles erreurs.
Vérifiez le front-end (RUM) :
Allez dans "Frontend". Vous devriez voir une nouvelle application détectée, correspondant à votre application Angular.
Naviguez dans votre application sur localhost:4200. Dynatrace commencera à collecter les données sur les actions des utilisateurs, les performances de chargement des pages et les appels XHR/Fetch.
Le OneAgent sur votre processus Java aura automatiquement injecté le tag RUM dans le index.html servi (même par ng serve, car les requêtes passent par le même hôte). Vous pourrez voir la relation directe entre une action utilisateur dans le front-end et la transaction back-end qu'elle a déclenchée (c'est la magie de la "Distributed Tracing" de Dynatrace).
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
///////////////////////////// simuler la creation d'un certificat /////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////
Pour être clair, Dynatrace ne va pas exécuter la logique de création du certificat lui-même. Votre application (Angular + Spring Boot) reste responsable de cela. Le rôle de Dynatrace est de :
Simuler un utilisateur qui demande la création d'un certificat.
Surveiller de bout en bout (front-end -> back-end -> Horizon) cette transaction spécifique.
Valider que le processus s'est bien déroulé et dans des temps acceptables.
Vous alerter si le processus échoue ou devient trop lent.
Pour ce faire, nous allons utiliser une des fonctionnalités les plus puissantes de Dynatrace : le Monitoring Synthétique (Synthetic Monitoring).
Voici le plan d'action détaillé :
Étape 1 : S'assurer que le Traçage de Bout en Bout (PurePath) fonctionne
Avant de simuler, il faut vérifier que Dynatrace "voit" déjà correctement le flux lorsque vous le faites manuellement.
Lancez votre application avec le script start_app_with_dynatrace.sh.
Ouvrez votre application Angular dans votre navigateur.
Effectuez manuellement l'action de création de certificat.
Allez dans votre interface Dynatrace.
Cherchez le "PurePath" correspondant. Vous devriez voir une trace distribuée qui ressemble à ceci :
Action Utilisateur (ex: Click on "Créer Certificat") dans votre application Angular.
Appel HTTP (XHR) vers votre back-end Spring Boot (ex: POST /api/certificates).
Service Method dans votre code Java qui gère la requête.
Appel HTTP sortant depuis votre back-end vers l'API d'Horizon.
Si vous voyez cette chaîne complète, le traçage de base est fonctionnel. C'est la fondation.
Étape 2 : Mettre en Évidence cette Transaction comme un Processus Métier
Pour que Dynatrace comprenne que "la création de certificat" est une action importante, vous pouvez la marquer comme une Action Utilisateur Clé (Key User Action).
Dans Dynatrace, allez dans la section "Frontend".
Sélectionnez votre application Angular.
Cliquez sur "View top user actions".
Trouvez l'action qui correspond au clic sur le bouton de création (par exemple, "Click on button 'Créer'").
Cliquez sur cette action, puis marquez-la comme "Key user action".
Maintenant, Dynatrace suivra spécifiquement le taux de succès, la performance et l'activité de cette action sur vos dashboards.
Étape 3 : Créer un Moniteur Synthétique pour Simuler le Processus
C'est ici que nous allons automatiser et simuler le workflow. Nous allons créer un "robot" qui, à intervalles réguliers, va se comporter comme un utilisateur et tenter de créer un certificat.
Accéder au Monitoring Synthétique :
Dans le menu de gauche de Dynatrace, allez dans Synthetic.
Créer un nouveau moniteur :
Cliquez sur le bouton Create a synthetic monitor.
Choisissez Browser monitor (moniteur de navigateur). C'est le plus adapté car il simule un vrai utilisateur interagissant avec votre interface Angular.
Configurer le "Clickpath" (le parcours utilisateur) :
Donnez un nom à votre moniteur, par exemple : Workflow de Création de Certificat.
Entrez l'URL de votre application.
Cliquez sur Record clickpath (Enregistrer le parcours).
Enregistrer la simulation :
Une fenêtre de navigateur spéciale va s'ouvrir. Dynatrace va enregistrer toutes vos actions.
Action 1 : S'authentifier (si nécessaire). Entrez un nom d'utilisateur et un mot de passe de test.
Action 2 : Naviguer. Allez sur la page où se trouve le formulaire de création de certificat.
Action 3 : Remplir le formulaire. Saisissez les informations nécessaires pour le certificat (nom, domaine, etc.).
Action 4 : Cliquer sur le bouton "Créer".
Action 5 : Valider le succès (TRÈS IMPORTANT). C'est l'étape qui détermine si le test a réussi ou échoué. Après avoir cliqué sur "Créer", une notification de succès doit apparaître (ex: "Certificat créé avec succès !").
Dans l'enregistreur Dynatrace, ajoutez un événement de validation.
Sélectionnez "Validate".
Demandez-lui de vérifier la présence du texte "Certificat créé avec succès !" sur la page. Vous pouvez aussi valider la présence d'un élément HTML spécifique (ex: un div avec la classe alert-success).
Sauvegarder et Configurer :
Une fois l'enregistrement terminé, sauvegardez le clickpath.
Fréquence : Choisissez à quelle fréquence le test doit s'exécuter (ex: toutes les 15 minutes, toutes les heures...).
Locations : Choisissez depuis quels endroits du monde vous voulez que la simulation soit lancée (les "locations" sont des serveurs Dynatrace qui hébergent les robots).
Alertes : Configurez les alertes. Par exemple, "alertez-moi si le test échoue 3 fois de suite".
Ce que vous obtenez avec cette configuration
Une fois le moniteur synthétique actif, vous aurez :
Monitoring Proactif : Dynatrace teste en continu votre processus de création de certificat, même quand aucun utilisateur réel n'est sur le site. Si Horizon est en panne, ou si une nouvelle version de votre back-end a cassé l'API, vous serez le premier à le savoir.
Mesure de la Performance de Bout en Bout : Pour chaque exécution du test, vous aurez des métriques précises sur la durée de chaque étape (chargement de la page, remplissage du formulaire, réponse de l'API, etc.).
Disponibilité du Service : Vous aurez un dashboard montrant le pourcentage de disponibilité de votre fonctionnalité "création de certificat" (ex: 99.9% de succès sur les dernières 24h).
Analyse d'Erreur Automatisée : Si un test échoue, Dynatrace ne vous donnera pas seulement une alerte. Il vous fournira :
Une capture d'écran de la page au moment de l'erreur.
Le PurePath complet, vous montrant exactement où le problème est survenu (dans le front-end, le back-end, ou lors de l'appel à Horizon).
Les logs et les exceptions Java correspondants.
En résumé, pour que Dynatrace simule et surveille ce processus :
Vérifiez que le traçage de base est correct en le faisant manuellement.
Créez un Moniteur Synthétique de type "Browser Monitor".
Enregistrez le parcours utilisateur complet : connexion, remplissage du formulaire, clic sur le bouton.
Ajoutez une étape de validation pour vérifier l'apparition d'un message de succès.
Planifiez l'exécution du moniteur et configurez les alertes.
Vous aurez ainsi transformé une surveillance technique en une surveillance de processus métier critique.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
//////////////////////////// le OneAgent /////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////////////////////
Pensez au OneAgent comme à un agent secret de renseignement ou un médecin expert que vous installez une seule fois sur vos serveurs (physiques, virtuels, ou dans vos conteneurs). Une fois installé, il fait tout le travail de surveillance pour vous, de manière entièrement automatique.
En termes simples, le OneAgent est le composant de collecte de données de la plateforme Dynatrace. C'est le "bras armé" de Dynatrace sur le terrain, celui qui voit, écoute et rapporte tout ce qui se passe dans votre système.
Voici ses caractéristiques clés qui le rendent si puissant et différent des autres agents de monitoring :
1. "One" - Un Seul Agent pour Tout
C'est la raison de son nom. Avant Dynatrace, pour surveiller une application complexe, il fallait installer plusieurs agents spécialisés :
Un agent pour surveiller le CPU et la RAM du serveur.
Un agent pour surveiller le serveur web (Apache, Nginx).
Un agent pour instrumenter le code Java (APM).
Un agent pour lire les fichiers de logs.
Un script à ajouter manuellement pour le monitoring utilisateur (RUM).
Le OneAgent remplace tout cela. Un seul processus léger s'occupe de surveiller toutes les couches de votre système, de l'infrastructure jusqu'au code applicatif.
2. "Automatique" - Déploiement et Instrumentation Sans Effort
C'est sa plus grande force. Une fois que vous lancez l'installeur du OneAgent :
Découverte automatique : Il scanne le serveur et identifie automatiquement tous les processus en cours d'exécution : votre application Spring Boot (JVM), votre base de données, votre serveur web, etc.
Instrumentation automatique : Il "s'injecte" dynamiquement dans ces processus sans que vous ayez à modifier une seule ligne de votre code. Pour votre Spring Boot, il s'attache à la JVM. Pour votre front-end Angular, il injecte le tag JavaScript RUM automatiquement dans le HTML servi par votre back-end. C'est ce qu'on appelle l'instrumentation "zero-touch".
Mise à jour automatique : Il se met à jour tout seul, vous n'avez pas à le gérer.
3. "Intelligent" - Contexte et Topologie (Smartscape®)
Le OneAgent ne se contente pas de remonter des métriques isolées (comme "le CPU est à 80%"). Il comprend les relations et les dépendances entre tous les composants.
Il sait que votre application Angular (exécutée dans le navigateur d'un utilisateur) parle à votre service Spring Boot.
Il sait que ce service Spring Boot dépend d'une base de données PostgreSQL.
Il sait que tout cela tourne sur un serveur Linux hébergé sur AWS.
Toutes ces informations sont envoyées à Dynatrace pour construire une carte dynamique et en temps réel de votre système, appelée Smartscape®. C'est ce contexte qui permet à l'IA de Dynatrace (Davis®) de trouver la cause racine des problèmes, et non juste les symptômes.
4. "Full Stack" - Visibilité Complète
Le OneAgent collecte des données sur l'ensemble de la pile technologique :
Couche	Ce que le OneAgent collecte
Utilisateur Final	Expérience utilisateur réelle (RUM) : temps de chargement, erreurs JavaScript, actions cliquées.
Application	Traces distribuées (PurePath®) : le parcours complet d'une requête, du clic au code.
Services	Performance des microservices, dépendances, temps de réponse, taux d'erreur.
Processus	Santé des processus comme les JVM, Node.js, PHP, .NET, etc.
Conteneurs	Performance des conteneurs Docker, Kubernetes (ressources, redémarrages...).
Infrastructure	Métriques classiques : CPU, mémoire, disque, réseau du serveur hôte.
Logs	Capture et analyse centralisée des fichiers de logs de vos applications.
En Résumé
Le OneAgent n'est pas juste un collecteur de métriques. C'est le système nerveux de votre environnement applicatif qui :
S'installe une seule fois par hôte.
Découvre et instrumente automatiquement toutes vos technologies sans modification de code.
Comprend les relations entre tous les composants.
Remonte une vue complète et contextuelle de la performance à l'IA de Dynatrace.
C'est grâce à lui que vous pouvez passer d'une simple surveillance à une observabilité intelligente et automatisée.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
////////////////////////////////////// integration a dynatrace ////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////////////////
Excellente question ! "Intégrer un script Bash à Dynatrace" signifie que vous voulez que Dynatrace soit conscient de l'exécution, du succès ou de l'échec de votre script. C'est crucial pour la surveillance des tâches de fond, des déploiements CI/CD, des sauvegardes, etc.
Votre script Bash est un processus externe. Pour que Dynatrace le "voie", vous devez lui envoyer des informations activement. La méthode la plus courante et la plus puissante est d'utiliser l'API de Dynatrace.
Nous allons nous concentrer sur la méthode la plus efficace : envoyer un événement personnalisé (Custom Event) à Dynatrace depuis votre script. Cet événement peut signaler le début, la fin, le succès ou l'échec de votre tâche.
Voici un guide complet, étape par étape.
Scénario :
Imaginons que votre script start_app_with_dynatrace.sh est maintenant une tâche de déploiement. Nous voulons que Dynatrace sache :
Quand le déploiement a commencé.
Si le déploiement a réussi ou échoué.
Sur quelle machine (hôte) le déploiement a eu lieu.
Prérequis : Créer un Jeton d'API Dynatrace
Vous avez besoin d'une clé pour autoriser votre script à communiquer avec Dynatrace.
Dans votre interface Dynatrace, allez dans Manage > Access tokens.
Cliquez sur Generate new token.
Donnez-lui un nom clair, par exemple bash_script_integration.
Dans la section API v2 scopes, activez la permission suivante :
events.ingest (Ingérer des événements)
Cliquez sur Generate token.
Copiez immédiatement le jeton et conservez-le en lieu sûr. Vous ne pourrez plus le voir après avoir quitté cette page.
Méthode 1 : Intégration via l'API d'Événements (Recommandé)
Nous allons modifier votre script pour qu'il envoie des requêtes curl à l'API Dynatrace.
Étape 1 : Préparer les informations pour l'API
Ajoutez ces variables au début de votre script :
code
Bash
# --- Configuration Dynatrace API (À MODIFIER) ---
# Remplacez XXXXXX par votre ID d'environnement Dynatrace.
# Vous le trouvez dans l'URL : https://XXXXXX.live.dynatrace.com
DYNATRACE_ENV_URL="https://XXXXXX.live.dynatrace.com" 

# Collez le jeton d'API que vous venez de créer
DYNATRACE_API_TOKEN="dt0c01.XXXXXXXXXXXXXXXX..."
Étape 2 : Créer une fonction pour envoyer les événements
Ajouter une fonction réutilisable rend le script beaucoup plus propre.
code
Bash
# --- Fonction d'intégration Dynatrace ---

# Fonction pour envoyer un événement personnalisé à l'API Dynatrace v2
# Arguments:
#   $1: Le titre de l'événement (ex: "Déploiement Réussi")
#   $2: Le type d'événement ("SUCCESS", "FAILURE", "INFO")
#   $3: Un objet JSON de propriétés personnalisées (ex: '{"script_version":"1.2"}')
send_dynatrace_event() {
    local EVENT_TITLE="$1"
    local EVENT_TYPE="$2"
    local CUSTOM_PROPERTIES="$3"

    # Détecte automatiquement l'ID de l'hôte où le script s'exécute.
    # Nécessite que le OneAgent soit installé pour que ce fichier existe.
    local HOST_ID=$(cat /var/lib/dynatrace/oneagent/agent/config/hostautotag.conf | grep 'HostId=' | cut -d'=' -f2)

    # Construction du JSON à envoyer
    local JSON_PAYLOAD=$(cat <<EOF
{
  "eventType": "CUSTOM_DEPLOYMENT",
  "title": "${EVENT_TITLE}",
  "entitySelector": "type(HOST),entityId(${HOST_ID})",
  "properties": {
    "Script Name": "$(basename "$0")",
    "Deployment Status": "${EVENT_TYPE}",
    "Triggered by": "$(whoami)",
    "Custom Properties": ${CUSTOM_PROPERTIES:-{\}}
  }
}
EOF
)

    echo "Envoi de l'événement à Dynatrace : ${EVENT_TITLE}"

    # Appel à l'API avec curl
    curl -X POST "${DYNATRACE_ENV_URL}/api/v2/events/ingest" \
         -H "Authorization: Api-Token ${DYNATRACE_API_TOKEN}" \
         -H "Content-Type: application/json" \
         -d "${JSON_PAYLOAD}"
}
Explication du JSON envoyé :
eventType: Un type que vous définissez. CUSTOM_DEPLOYMENT est un bon choix.
title: Le message qui apparaîtra dans l'interface Dynatrace.
entitySelector: C'est la partie la plus importante. Elle lie l'événement à une entité surveillée par Dynatrace. Ici, nous le lions à l'hôte sur lequel le script s'exécute.
properties: Des métadonnées clés/valeurs qui ajoutent du contexte. Très utile pour le diagnostic.
Étape 3 : Utiliser la fonction dans votre script
Maintenant, appelons cette fonction aux moments clés.
Voici à quoi pourrait ressembler votre script start_app_with_dynatrace.sh modifié :
code
Bash
#!/bin/bash
set -e # Arrête le script si une commande échoue

# --- Configuration (chemins, etc.) ---
# ... (vos variables de configuration ici) ...

# --- Configuration Dynatrace API (À MODIFIER) ---
DYNATRACE_ENV_URL="https://XXXXXX.live.dynatrace.com" 
DYNATRACE_API_TOKEN="dt0c01.XXXXXXXXXXXXXXXX..." 

# --- Fonction d'intégration Dynatrace ---
# ... (collez la fonction send_dynatrace_event d'en haut ici) ...

# --- Logique du Script ---

# Utilise 'trap' pour intercepter les erreurs ou la fin normale du script
cleanup() {
    EXIT_CODE=$?
    if [ "$EXIT_CODE" = "0" ]; then
        send_dynatrace_event "Déploiement et Démarrage Réussi" "SUCCESS" '{"app_version":"2.1.0"}'
    else
        send_dynatrace_event "ÉCHEC du Déploiement et Démarrage" "FAILURE" '{"error_code":"'${EXIT_CODE}'"}'
    fi
    exit $EXIT_CODE
}
trap cleanup EXIT

# ---- Début de l'exécution ----

send_dynatrace_event "Début du déploiement de l'application" "INFO" '{}'

echo "Build du Backend..."
cd "$BACK_DIR"
mvn clean package -DskipTests

echo "Démarrage du Backend avec Dynatrace..."
java -javaagent:"$DYNATRACE_AGENT_PATH" -jar "$BACKEND_JAR_PATH" &
sleep 15 # Laisse le temps de démarrer

echo "Démarrage du Frontend..."
cd "$FRONT_DIR"
ng serve --proxy-config proxy.conf.json --open &

echo "L'application est en cours de démarrage."
# La fonction 'cleanup' sera appelée automatiquement à la fin du script (même sur CTRL+C)
# Si vous voulez que le script se termine ici et rapporte le succès, vous pouvez ajouter 'exit 0'
# Si vous le laissez tourner, le rapport ne sera envoyé qu'à l'arrêt.

# Pour un script de déploiement qui se termine, la logique est parfaite.
# Par exemple, nous pouvons simuler une attente et ensuite déclarer le succès.
echo "Attente de 60 secondes pour la stabilisation..."
sleep 60
echo "Déploiement terminé."

# La fin du script déclenchera le trap 'cleanup' qui enverra l'événement de succès.
Comment ça marche et où le voir ?
Exécutez votre script.
Dans l'interface Dynatrace, allez sur l'hôte où le script a été exécuté (Hosts > Votre Hôte).
Sur la page de l'hôte, vous verrez une carte "Events". Votre événement personnalisé apparaîtra ici, avec le titre et les propriétés que vous avez définis.
Vous pouvez cliquer sur l'événement pour voir tous les détails.
Méthode 2 : Intégration via les Fichiers de Logs
Si votre script produit déjà un fichier de log, vous pouvez demander à Dynatrace de le surveiller.
Assurez-vous que Log Monitoring est activé pour votre hôte dans Dynatrace.
Configurez OneAgent pour surveiller votre fichier de log :
Allez dans Settings > Log Monitoring > Log sources and storage.
Ajoutez une nouvelle règle pour pointer vers le fichier de log de votre script (ex: /var/log/deployment.log).
Créez des règles de traitement de log :
Allez dans Settings > Log Monitoring > Log processing.
Créez une règle qui recherche un certain texte dans les logs (ex: une ligne contenant "DEPLOYMENT_FAILED").
Configurez cette règle pour créer un événement personnalisé lorsque ce texte est trouvé.
Cette méthode est excellente si vous voulez conserver le contexte des logs autour de l'événement d'erreur.
Quand Utiliser Quelle Méthode ?
Méthode	Avantages	Inconvénients	Idéal pour...
API d'Événements	Très flexible, contrôle total sur le contenu, immédiat, ne nécessite pas la surveillance de logs.	Nécessite de modifier le script pour ajouter des appels curl.	Scripts CI/CD, tâches de déploiement, jobs cron, où un statut clair (succès/échec) est nécessaire.
Fichiers de Logs	Utilise les logs existants, fournit un contexte riche autour de l'erreur.	Moins direct, dépend de la configuration de la surveillance des logs.	Scripts complexes qui produisent des logs détaillés, analyse post-mortem.
Pour votre cas, l'approche par API est la plus directe et la plus puissante. Elle transforme votre script Bash en un citoyen de première classe dans votre écosystème d'observabilité Dynatrace.
///////////////////////// guide complet /////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////
Absolument. Voici un guide complet et consolidé qui combine toutes les étapes dans un ordre logique. Il vous permettra de partir de zéro pour lancer, surveiller et simuler votre application Angular + Spring Boot avec Dynatrace, en utilisant un seul script Bash comme point central.
Le Plan d'Action Complet en 5 Étapes
Objectif Final : Créer un système où un script Bash lance votre application full-stack, s'assure qu'elle est surveillée par Dynatrace, et où Dynatrace vérifie de manière proactive que votre fonctionnalité métier la plus critique (la création de certificat) fonctionne en permanence.
Étape 1 : Préparation de l'Environnement et des Prérequis
C'est la fondation. Sans cela, rien ne fonctionnera.
Installer Dynatrace OneAgent :
Quoi ? Le OneAgent est le collecteur de données de Dynatrace. C'est un programme qui doit être installé sur la machine (serveur, VM) où votre back-end Spring Boot s'exécutera.
Comment ? Suivez les instructions de votre interface Dynatrace sous Deploy Dynatrace > Start installation. Choisissez votre système d'exploitation (Linux, Windows...).
Vérification : Sur Linux, assurez-vous que le processus oneagent est en cours d'exécution et que le fichier /opt/dynatrace/oneagent/agent/lib64/oneagentjava.jar existe.
Préparer le projet Angular (Front-end) :
À la racine de votre projet Angular, créez un fichier nommé proxy.conf.json. Ce fichier est crucial pour que le serveur de développement Angular redirige les appels API vers votre back-end Spring Boot, évitant ainsi les erreurs de CORS.
Contenu de proxy.conf.json :
code
JSON
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false
  }
}
(Adaptez /api et localhost:8080 si nécessaire).
Préparer un jeton d'API Dynatrace :
Pourquoi ? Pour permettre à notre script Bash d'envoyer des notifications (événements) à Dynatrace (par exemple, "le déploiement a commencé").
Comment ? Dans Dynatrace, allez dans Manage > Access tokens > Generate new token. Donnez-lui un nom (bash_script_token) et cochez la permission events.ingest. Copiez et gardez le jeton en lieu sûr.
Étape 2 : Création du Script Bash d'Orchestration et d'Intégration
Ce script est le cœur de notre automatisation. Il va builder, lancer les applications et communiquer avec Dynatrace.
Créez un fichier start_full_app.sh et collez-y le code suivant. Lisez attentivement les commentaires et modifiez les variables dans la section CONFIGURATION.
code
Bash
#!/bin/bash

# ====================================================================================
# SCRIPT DE DÉPLOIEMENT ET DE DÉMARRAGE POUR UNE APPLICATION ANGULAR + SPRING BOOT
#               AVEC INSTRUMENTATION ET INTÉGRATION DYNATRACE
# ====================================================================================

# Arrête le script immédiatement si une commande échoue
set -e

# --- CONFIGURATION (À MODIFIER SELON VOTRE ENVIRONNEMENT) ---

# 1. Chemins vers vos projets
FRONT_DIR="/chemin/vers/votre/projet/angular"
BACK_DIR="/chemin/vers/votre/projet/spring-boot"

# 2. Informations sur le projet Backend
BACKEND_JAR_NAME="votre-application-0.0.1-SNAPSHOT.jar" # Nom du JAR après le build

# 3. Configuration Dynatrace
# Chemin vers l'agent Java de Dynatrace (vérifiez s'il est correct pour votre système)
DYNATRACE_AGENT_PATH="/opt/dynatrace/oneagent/agent/lib64/oneagentjava.jar"
# URL de votre environnement Dynatrace (ex: https://abc12345.live.dynatrace.com)
DYNATRACE_ENV_URL="https://VOTRE_ID.live.dynatrace.com" 
# Jeton d'API Dynatrace avec la permission "events.ingest"
DYNATRACE_API_TOKEN="dt0c01.XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"

# --- FONCTIONS UTILITAIRES ---

# Fonction pour envoyer un événement personnalisé à l'API Dynatrace
send_dynatrace_event() {
    local EVENT_TITLE="$1"
    local EVENT_TYPE="$2"
    
    # Détecte l'ID de l'hôte où le script s'exécute grâce au OneAgent
    local HOST_ID
    if [ -f /var/lib/dynatrace/oneagent/agent/config/hostautotag.conf ]; then
        HOST_ID=$(cat /var/lib/dynatrace/oneagent/agent/config/hostautotag.conf | grep 'HostId=' | cut -d'=' -f2)
    else
        echo "AVERTISSEMENT : Impossible de trouver l'ID de l'hôte Dynatrace. L'événement ne sera pas lié à l'hôte."
        return
    fi

    local JSON_PAYLOAD=$(cat <<EOF
{
  "eventType": "CUSTOM_DEPLOYMENT",
  "title": "${EVENT_TITLE}",
  "entitySelector": "type(HOST),entityId(${HOST_ID})",
  "properties": { "Script Name": "$(basename "$0")", "Status": "${EVENT_TYPE}", "Triggered by": "$(whoami)" }
}
EOF
)
    echo ">> Dynatrace: Envoi de l'événement '${EVENT_TITLE}'"
    curl -s -X POST "${DYNATRACE_ENV_URL}/api/v2/events/ingest" \
         -H "Authorization: Api-Token ${DYNATRACE_API_TOKEN}" -H "Content-Type: application/json" \
         -d "${JSON_PAYLOAD}" > /dev/null
}

# Fonction de nettoyage appelée à la fin du script
cleanup() {
    EXIT_CODE=$?
    echo ""
    echo "----------------------------------------------------"
    echo "Arrêt des applications..."
    if [ "$EXIT_CODE" = "0" ]; then
        send_dynatrace_event "Application arrêtée proprement" "INFO"
    else
        send_dynatrace_event "ERREUR: Le script s'est terminé avec le code ${EXIT_CODE}" "FAILURE"
    fi
    kill 0 # Arrête tous les processus enfants (java, ng serve)
    exit $EXIT_CODE
}

# Intercepte CTRL+C et les fins de script pour exécuter le nettoyage
trap cleanup INT TERM EXIT

# --- EXÉCUTION PRINCIPALE ---

echo "----------------------------------------------------"
echo "Vérification des prérequis..."
echo "----------------------------------------------------"
if [ ! -f "$DYNATRACE_AGENT_PATH" ]; then
    echo "ERREUR: L'agent Dynatrace est introuvable. Vérifiez le chemin et l'installation."
    exit 1
fi
echo "Prérequis OK."

send_dynatrace_event "Début du déploiement de l'application" "INFO"

# 1. Builder le Backend
echo "----------------------------------------------------"
echo "1. Build du Backend (Spring Boot)..."
echo "----------------------------------------------------"
cd "$BACK_DIR"
mvn clean package -DskipTests
BACKEND_JAR_PATH="$BACK_DIR/target/$BACKEND_JAR_NAME"
echo "Build du Backend terminé."

# 2. Démarrer le Backend avec l'instrumentation Dynatrace
echo "----------------------------------------------------"
echo "2. Démarrage du Backend avec l'agent Dynatrace..."
echo "----------------------------------------------------"
# L'option -javaagent est la clé pour le monitoring du back-end
java -javaagent:"$DYNATRACE_AGENT_PATH" -jar "$BACKEND_JAR_PATH" &
BACKEND_PID=$!
echo "Backend démarré avec le PID: $BACKEND_PID. Attente de 15 secondes..."
sleep 15

# 3. Démarrer le Frontend
echo "----------------------------------------------------"
echo "3. Démarrage du Frontend (Angular)..."
echo "----------------------------------------------------"
cd "$FRONT_DIR"
# L'option --proxy-config est la clé pour la communication front-back
ng serve --proxy-config proxy.conf.json --open &
FRONTEND_PID=$!
echo "Frontend démarré avec le PID: $FRONTEND_PID"

send_dynatrace_event "Application démarrée avec succès" "SUCCESS"

echo ""
echo "======================================================================="
echo "  🚀 APPLICATION EN COURS D'EXÉCUTION 🚀"
echo "  Frontend accessible sur : http://localhost:4200"
echo "  Backend sur             : http://localhost:8080"
echo "  Monitoring actif sur Dynatrace."
echo "  Appuyez sur CTRL+C pour tout arrêter proprement."
echo "======================================================================="

# Attend indéfiniment que l'utilisateur appuie sur CTRL+C.
# La fonction 'cleanup' se chargera de tout arrêter.
wait
Étape 3 : Exécution du Script et Vérification Initiale dans Dynatrace
Rendre le script exécutable :
code
Sh
chmod +x start_full_app.sh
Lancer le script :
code
Sh
./start_full_app.sh
Observer le terminal : Le script va builder, lancer le back-end puis le front-end, et un navigateur s'ouvrira.
Vérifier dans Dynatrace :
Événements : Allez sur la page de l'hôte (Hosts > nom de votre machine). Vous devriez voir les événements : "Début du déploiement..." et "Application démarrée avec succès".
Services Back-end : Allez dans Transactions and services. Votre service Java Spring Boot doit apparaître. Cliquez dessus.
Application Front-end : Allez dans Frontend. Votre application Angular doit apparaître.
Test Manuel : Utilisez votre application pour créer un certificat manuellement. Ensuite, dans Dynatrace, trouvez la trace correspondante (Distributed traces) pour confirmer que vous voyez bien le flux complet : Action Utilisateur (Angular) -> Appel API -> Service (Spring Boot) -> Appel vers Horizon.
Étape 4 : Création d'un Moniteur Synthétique pour Simuler le Processus Métier
Maintenant que l'application est surveillée, nous allons demander à Dynatrace de simuler un utilisateur qui crée un certificat à intervalles réguliers.
Aller à Synthetic : Dans le menu Dynatrace, cliquez sur Synthetic.
Créer un moniteur : Cliquez sur Create a synthetic monitor et choisissez Browser monitor.
Enregistrer le parcours (Clickpath) :
Donnez un nom : Simulation de Création de Certificat.
Entrez l'URL de votre application (ex: http://votre_ip:4200).
Cliquez sur Record clickpath.
Dans la fenêtre d'enregistrement qui s'ouvre, effectuez les actions exactes d'un utilisateur :
a. Connectez-vous si nécessaire.
b. Naviguez vers le formulaire de création.
c. Remplissez les champs du formulaire.
d. Cliquez sur le bouton "Créer".
Ajouter une validation (étape cruciale) : Une fois l'action de création terminée, un message de succès doit s'afficher. Dans la barre d'outils de l'enregistreur, ajoutez une Validation pour vérifier que le texte "Certificat créé avec succès" (ou un message similaire) est bien présent sur la page.
Configurer et Activer :
Sauvegardez l'enregistrement.
Choisissez la fréquence (ex: toutes les 30 minutes).
Choisissez les locations (les endroits d'où les tests seront lancés).
Activez le moniteur.
Étape 5 : Exploitation des Données et Alertes
Vous avez maintenant un système complet. Voici comment l'utiliser :
Tableau de bord de disponibilité : Le moniteur synthétique vous donne un pourcentage de disponibilité de votre fonctionnalité de création de certificat. Vous pouvez ajouter ce graphique à vos dashboards.
Alertes proactives : Si la simulation échoue (parce que l'API Horizon est en panne, ou que votre back-end a une erreur), Dynatrace vous enverra une alerte avant même qu'un utilisateur ne s'en plaigne.
Diagnostic d'erreur : En cas d'échec, le ticket de problème Dynatrace vous montrera :
Le PurePath complet pour identifier la cause racine.
Des captures d'écran de l'erreur côté navigateur.
Les logs pertinents du back-end au moment de l'erreur.
Suivi de performance : Vous pouvez suivre l'évolution du temps de réponse de cette fonctionnalité critique dans le temps et détecter les régressions de performance.
Vous avez ainsi mis en place une boucle complète d'observabilité : déploiement automatisé, surveillance full-stack, et validation proactive des processus métier.
Model
ThinkingThoughts
(experimental)
Auto
Expand to view model thoughts

chevron_right
Absolument ! Voici un guide complet et unifié qui combine toutes les étapes de manière ordonnée. Il contient un seul script Bash finalisé et vous explique comment le mettre en place et ce que vous verrez dans Dynatrace.
L'Objectif Final
Nous allons créer un seul script Bash qui accomplit tout ce qui suit :
Informe Dynatrace que le processus de déploiement/démarrage commence.
Compile (build) votre application back-end Spring Boot.
Démarre le back-end en l'attachant à l'agent Dynatrace OneAgent pour une surveillance complète (APM).
Démarre le front-end Angular, qui sera automatiquement surveillé par Dynatrace via le RUM (Real User Monitoring).
Informe Dynatrace si le processus a réussi ou échoué.
Phase 1 : Prérequis (À faire une seule fois)
Avant de lancer le script, assurez-vous que tout est en place.
Dynatrace OneAgent Installé : Le OneAgent doit être installé sur la machine où vous exécutez le script. Sous Linux, son chemin est généralement /opt/dynatrace/oneagent/.
Créer un Jeton d'API Dynatrace :
Dans Dynatrace, allez dans Manage > Access tokens.
Cliquez sur Generate new token.
Nommez-le script_bash_deploiement.
Activez la permission events.ingest (API v2).
Générez et copiez le jeton. Vous en aurez besoin pour le script.
Préparer votre Front-end Angular :
À la racine de votre projet Angular, créez un fichier nommé proxy.conf.json. C'est essentiel pour que votre front-end (sur le port 4200) puisse parler à votre back-end (sur le port 8080) sans problème de CORS.
Contenu de proxy.conf.json :
code
JSON
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "logLevel": "debug"
  }
}
(Adaptez /api si le préfixe de vos URLs d'API est différent).
Outils Installés : Assurez-vous d'avoir Java, Maven (ou Gradle), et Node.js/Angular CLI installés.
Phase 2 : Le Script Bash Complet et Commenté
Copiez ce code dans un fichier nommé lancer_mon_application.sh. C'est la seule chose que vous aurez à exécuter.
code
Bash
#!/bin/bash

# ===================================================================================
# Script unifié pour Builder, Lancer et Surveiller une application Full-Stack
# (Angular + Spring Boot) avec une intégration complète à Dynatrace.
# ===================================================================================

# Arrête le script immédiatement si une commande échoue
set -e

# --- CONFIGURATION (MODIFIEZ CETTE SECTION) ---

# 1. Chemins vers vos projets
FRONT_DIR="/chemin/absolu/vers/votre/projet/angular"
BACK_DIR="/chemin/absolu/vers/votre/projet/spring-boot"

# 2. Informations sur le projet Backend
# Le nom du fichier .jar généré dans le dossier 'target' après le build
BACKEND_JAR_NAME="votre-application-0.0.1-SNAPSHOT.jar"

# 3. Configuration de l'API Dynatrace
# Remplacez XXXXXX par l'ID de votre environnement (visible dans l'URL)
DYNATRACE_ENV_URL="https://XXXXXX.live.dynatrace.com"
# Collez le jeton d'API que vous avez créé à l'étape des prérequis
DYNATRACE_API_TOKEN="dt0c01.XXXXXXXXXXXXXXXX..."

# 4. Chemin vers l'agent Dynatrace (ne pas changer si l'installation est standard)
DYNATRACE_AGENT_PATH="/opt/dynatrace/oneagent/agent/lib64/oneagentjava.jar"

# --- FONCTIONS INTERNES (NE PAS MODIFIER) ---

# Fonction pour envoyer des notifications à l'API Dynatrace
send_dynatrace_event() {
    local EVENT_TITLE="$1"
    local EVENT_TYPE="$2" # Ex: SUCCESS, FAILURE, INFO

    # Détecte automatiquement l'ID de l'hôte surveillé par Dynatrace
    local HOST_ID
    if [ -f /var/lib/dynatrace/oneagent/agent/config/hostautotag.conf ]; then
        HOST_ID=$(cat /var/lib/dynatrace/oneagent/agent/config/hostautotag.conf | grep 'HostId=' | cut -d'=' -f2)
    else
        echo "AVERTISSEMENT: Impossible de trouver l'ID de l'hôte. L'événement ne sera pas lié à une entité."
        return
    fi

    # Prépare le corps de la requête JSON pour l'API
    local JSON_PAYLOAD
    JSON_PAYLOAD=$(cat <<EOF
{
  "eventType": "CUSTOM_DEPLOYMENT",
  "title": "${EVENT_TITLE}",
  "entitySelector": "type(HOST),entityId(${HOST_ID})",
  "properties": {
    "Script": "$(basename "$0")",
    "Statut": "${EVENT_TYPE}",
    "Déclencheur": "$(whoami)@$(hostname)"
  }
}
EOF
)
    echo "----------------------------------------------------"
    echo "Envoi de la notification à Dynatrace : ${EVENT_TITLE}"
    echo "----------------------------------------------------"

    # Envoie la requête à l'API Dynatrace
    curl -s -o /dev/null -w "Réponse API Dynatrace: %{http_code}\n" -X POST "${DYNATRACE_ENV_URL}/api/v2/events/ingest" \
         -H "Authorization: Api-Token ${DYNATRACE_API_TOKEN}" \
         -H "Content-Type: application/json" \
         -d "${JSON_PAYLOAD}"
}

# Fonction de nettoyage qui s'exécute à la fin du script (succès, erreur ou CTRL+C)
cleanup() {
    local EXIT_CODE=$?
    echo ""
    echo "----------------------------------------------------"
    echo "Arrêt des processus en arrière-plan..."
    kill 0 2>/dev/null # Arrête tous les processus enfants du script

    if [ "$EXIT_CODE" = "0" ]; then
        send_dynatrace_event "Démarrage de l'application terminé avec SUCCÈS" "SUCCESS"
    else
        send_dynatrace_event "ÉCHEC du script de démarrage de l'application" "FAILURE"
    fi
    exit $EXIT_CODE
}

# 'trap' est un mécanisme qui exécute la fonction 'cleanup' quand le script se termine
trap cleanup EXIT

# --- EXÉCUTION PRINCIPALE ---

# Étape 1: Informer Dynatrace du début du processus
send_dynatrace_event "Début du build et déploiement de l'application" "INFO"

# Étape 2: Vérifier les prérequis
if [ ! -f "$DYNATRACE_AGENT_PATH" ]; then
    echo "ERREUR: L'agent Dynatrace OneAgent n'a pas été trouvé. Vérifiez le chemin."
    exit 1 # Le trap 'cleanup' va s'exécuter et envoyer un événement d'échec
fi

# Étape 3: Builder le back-end Spring Boot
echo "----------------------------------------------------"
echo "Build du Backend (Spring Boot)..."
echo "----------------------------------------------------"
cd "$BACK_DIR"
mvn clean package -DskipTests
BACKEND_JAR_PATH="$BACK_DIR/target/$BACKEND_JAR_NAME"

if [ ! -f "$BACKEND_JAR_PATH" ]; then
    echo "ERREUR: Le fichier JAR du backend n'a pas été trouvé après le build."
    exit 1
fi

# Étape 4: Démarrer le back-end en arrière-plan AVEC l'agent Dynatrace
echo "----------------------------------------------------"
echo "Démarrage du Backend avec l'instrumentation Dynatrace..."
echo "----------------------------------------------------"
java -javaagent:"$DYNATRACE_AGENT_PATH" -jar "$BACKEND_JAR_PATH" &
echo "Backend démarré en arrière-plan. Attente de stabilisation..."
sleep 20 # Laisse le temps au serveur Spring de démarrer complètement

# Étape 5: Démarrer le front-end en arrière-plan
echo "----------------------------------------------------"
echo "Démarrage du Frontend (Angular)..."
echo "----------------------------------------------------"
cd "$FRONT_DIR"
ng serve --proxy-config proxy.conf.json --open &

# Étape 6: Le script attend ici
echo ""
echo "======================================================================"
echo "L'application est en cours d'exécution et surveillée par Dynatrace."
echo "Frontend: http://localhost:4200"
echo "Backend:  http://localhost:8080"
echo ""
echo "Appuyez sur CTRL+C pour tout arrêter proprement."
echo "======================================================================"

# Attend que les processus en arrière-plan se terminent (par ex: via CTRL+C)
wait

# La fonction 'cleanup' est automatiquement appelée ici à la fin normale du 'wait'
# ou si l'utilisateur fait CTRL+C. Le 'exit 0' implicite à la fin du script
# signalera un succès.
Phase 3 : Comment l'Utiliser
Personnaliser le Script : Ouvrez lancer_mon_application.sh et modifiez uniquement la section --- CONFIGURATION --- avec vos propres chemins et informations Dynatrace.
Rendre le Script Exécutable : Dans votre terminal, une seule fois :
code
Sh
chmod +x lancer_mon_application.sh
Lancer l'Application :
code
Sh
./lancer_mon_application.sh
Votre terminal affichera les logs de build et de démarrage, et votre navigateur s'ouvrira automatiquement sur votre application Angular. Les deux serveurs tourneront jusqu'à ce que vous arrêtiez le script avec CTRL+C.
Phase 4 : Ce que vous verrez dans Dynatrace
Après avoir lancé le script et navigué un peu dans votre application :
Les Événements de Déploiement :
Allez dans Hosts et sélectionnez la machine où le script a tourné.
Dans le tableau de bord de l'hôte, vous verrez des marqueurs d'événements sur les graphiques. En bas, dans la section Events, vous verrez les notifications envoyées par le script : "Début du build...", "Démarrage... SUCCÈS".
La Surveillance du Back-end (APM) :
Allez dans Transactions and services.
Vous verrez un nouveau service Java détecté, correspondant à votre application Spring Boot. Dynatrace capture automatiquement toutes les requêtes API, les appels à la base de données et les erreurs. C'est le -javaagent qui a fait ce travail.
La Surveillance du Front-end (RUM) :
Allez dans Frontend.
Vous verrez votre application Angular détectée. Vous pourrez analyser les performances vues par l'utilisateur, les actions qu'il a effectuées (clics), et les erreurs JavaScript. Ceci a été possible car le OneAgent a automatiquement injecté le nécessaire dans le HTML.
La Vue de Bout en Bout (PurePath®) :
La vraie magie : lorsque vous analyserez une "action utilisateur" dans la section Frontend (ex: clic sur "Créer un certificat"), vous pourrez voir la trace complète (PurePath) qui montre l'appel JavaScript, la requête réseau, le traitement dans votre contrôleur Spring Boot, et même l'appel à la base de données, le tout dans une seule vue unifiée.
Vous avez maintenant un processus de démarrage robuste, automatisé et entièrement observable.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
