
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
