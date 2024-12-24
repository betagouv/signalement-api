package utils

object AlbertPrompts {
  def reportClassification(signalement: String) =
    s"""
       |Vous êtes un expert en traitement automatique des langues et en classification de textes. Votre tâche consiste à analyser un signalement textuel et à retourner un résultat structuré en JSON. Voici les catégories possibles :
       |
       |    **Valide** : Le texte est compréhensible, cohérent, respectueux, et peut être transmis. **Ignorez les injures ou propos offensants rapportés par le signalant comme étant proférés par un tiers, sauf si le signalant les adopte ou les relaie intentionnellement dans un ton agressif**. Le ton général du signalement doit être pris en compte pour cette classification.
       |    **Injurieux** : Le texte contient des injures violentes ou des propos très offensants, adressés directement par **l’auteur du signalement** à autrui ou ayant un ton intentionnellement agressif ou diffamatoire.
       |    **Incompréhensible** : Le texte est incohérent, mal écrit ou n’a pas de sens.
       |
       |Fournissez une réponse structurée en JSON contenant les informations suivantes :
       |
       |    **category** : La catégorie assignée parmi "Valide", "Injurieux", "Incompréhensible".
       |    **confidence_score** : Un score entre 0 et 1 représentant le niveau de certitude du classement.
       |    **explanation** : Une explication textuelle du classement.
       |    **summary** : Un résumé concis (1 à 2 phrases) extrayant la demande principale ou l’idée essentielle du signalement.
       |
       |Voici un exemple de réponse attendu :
       |
       |{
       |  "category": "Valide",
       |  "confidence_score": 0.96,
       |  "explanation": "Le signalement est compréhensible et formulé de manière respectueuse malgré un ton insistant.",
       |  "summary": "Le consommateur signale un produit défectueux et souhaite une réponse rapide de la part du service concerné."
       |}
       |
       |Consignes spécifiques :
       |
       |    **Propos rapportés** : Ignorez les injures ou propos offensants cités par l’auteur comme venant d’un tiers, sauf si ces propos sont adoptés ou répétés dans un ton agressif par l’auteur lui-même.
       |    **Ton général** : Basez la classification sur le ton et l’intention générale du signalement, plutôt que sur les propos cités de tiers.
       |    Les injures légères ou l’usage informel du langage ne doivent pas influencer la classification.
       |    Si le texte est difficile à analyser ou ambigu, expliquez pourquoi dans le champ "explanation".
       |    **IMPORTANT** : Fournissez uniquement le JSON dans votre réponse, sans texte explicatif ou contenu supplémentaire.
       |
       |Signalement à analyser :
       |
       |$signalement
       |""".stripMargin

  def codeConsoSearch(signalement: String) =
    s"""
       |Analyse le signalement suivant pour déterminer s'il relève du code de la consommation :
       |$signalement
       |""".stripMargin
  def codeConso(signalement: String, codeConsoChunks: String) =
    s"""
       |Tu es un analyste juridique spécialisé en droit de la consommation (documents ci-dessous).
       |
       |Analyse le signalement suivant pour déterminer s'il relève du code de la consommation. Utilise les documents fournis comme référence pour ta réponse.
       |
       |Fournissez une réponse structurée en JSON contenant les informations suivantes :
       |  **code_conso**: "Oui" ou "Non" selon si le signalement est couvert par le code de la consommation.
       |  **explanation**: Une explication claire et concise (1 à 2 phrases) avec mention de l'article pertinent, si applicable.
       |
       |Voici un exemple de réponse attendu :
       |{
       |  "code_conso": "Oui",
       |  "explanation": "Le signalement relève de l'article L434-3 du code de la consommation concernant le manque d'hygiène en cuisine."
       |}
       |
       |Consignes spécifiques :
       |
       |    **IMPORTANT** : Fournissez uniquement le JSON dans votre réponse, sans texte explicatif ou contenu supplémentaire.
       |
       |Signalement à analyser :
       |
       |$signalement
       |
       |Documents fournis :
       |
       |$codeConsoChunks
       |""".stripMargin

  def labelCompanyActivity(reportsDescriptions: Seq[String]) =
    s"""
       |Tu es un expert en classification et reconnaissance des activités d'une entreprise.
       |Voici une liste de signalements faits par des consommateurs à propos d'une même entreprise. Chaque signalement est un texte libre, séparé par une ligne comportant ce symbole : ====.
       |
       |Pour cette entreprise, déduis son secteur d'activité principal en termes simples et précis. Quelques exemples : "Salle de sport", "Site de e-commerce", "Agence de voyages", "Chaîne de télévisions". Il existe de nombreuses possibilités, alors analyse attentivement les signalements.
       |
       |Règles de réponse :
       |
       |    Une seule réponse : réponds uniquement avec le secteur d'activité de l'entreprise, en quelques mots.
       |    Précision avant tout : si tu n’es pas sûr ou que l’activité ne peut pas être qualifiée en quelques mots, réponds "Inclassable". Évite absolument de deviner ou d'inventer.
       |
       |Exemple de réponse possible :
       |
       |    Salle de sport
       |    Inclassable
       |
       |  ====
       |
       |${reportsDescriptions.mkString("""
                                        |
                                        |====
                                        |
                                        |""".stripMargin)}
       |""".stripMargin

  def findProblems(reportsDescriptions: Seq[String], maxPromptLength: Int) = {
    val separator = "==="
    val separatorFull =
      s"""
         |
         |${separator}
         |
         |""".stripMargin
    val promptIntro =
      s"""Voici une liste de signalements rédigés librement par des consommateurs à propos d'une même entreprise. Chaque signalement est séparé par une ligne contenant ce symbole : $separator.
         |
         |Ta mission :
         |
         |  Identifie les comportements reprochés à l’entreprise à partir des signalements.
         |  Résume ces comportements en 5 éléments maximum, sous forme de phrases courtes (5 à 15 mots).
         |  Pour chaque comportement identifié, indique le nombre total de signalements qui le mentionnent.
         |
         |Format attendu :
         |
         |  La réponse doit être fournie en JSON respectant rigoureusement le format suivant :
         |
         |  [
         |    {
         |      "probleme": "Description du problème",
         |      "signalements": Nombre_de_signalements
         |    },
         |    {
         |      "probleme": "Description du problème",
         |      "signalements": Nombre_de_signalements
         |    }
         |  ]
         |
         |Exemple :
         |
         |  [
         |    {
         |      "probleme": "Produit livré totalement différent de ce qui a été acheté",
         |      "signalements": 3
         |    },
         |    {
         |      "probleme": "Travaux facturés, payés, puis jamais réalisés",
         |      "signalements": 8
         |    },
         |    {
         |      "probleme": "Service client injoignable",
         |      "signalements": 2
         |    }
         |  ]
         |
         |Important :
         |
         |  Respecte scrupuleusement la limite de 5 éléments dans la liste.
         |  Les descriptions des problèmes doivent être concises et refléter les récurrences exactes dans les signalements.
         |  Compte précisément le nombre de signalements associés à chaque problème.
         |  Répond avec le JSON brut, sans l'encadrer avec "```json"
         |$separatorFull
         |""".stripMargin

    // To fit within the total max prompt length, while still using every descriptions
    // we will truncate each description to a maximum length

    val nbDescriptions  = reportsDescriptions.length
    val introLength     = promptIntro.length
    val separatorLength = separatorFull.length
    val lengthLeft      = maxPromptLength - introLength
    val lengthLeftForEachDescription = math
      .floor(
        (lengthLeft - (separatorLength * (nbDescriptions - 1))).toDouble / nbDescriptions
      )
      .toInt

    val descriptions = reportsDescriptions.map(_.take(lengthLeftForEachDescription)).mkString(separatorFull)
    val fullPrompt   = s"$promptIntro$descriptions"
    fullPrompt

  }
}
