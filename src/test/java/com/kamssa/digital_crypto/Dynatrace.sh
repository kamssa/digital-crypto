
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
