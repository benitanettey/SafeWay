package com.example.safeway

enum class ResourceDestination {
    UNDERSTAND_ABUSE,
    EVIDENCE_GUIDE,
    FIND_HELP_NEARBY,
    BREAK_STIGMA
}

data class ResourceSuggestion(
    val title: String,
    val destination: ResourceDestination
)

object ResourceRecommendationEngine {

    fun primaryDestinationFor(incidentType: String): ResourceDestination {
        return when (incidentType.lowercase()) {
            "physical", "sexual" -> ResourceDestination.EVIDENCE_GUIDE
            "verbal", "financial", "neglect" -> ResourceDestination.UNDERSTAND_ABUSE
            else -> ResourceDestination.UNDERSTAND_ABUSE
        }
    }

    fun suggestionsFor(incidentType: String): List<ResourceSuggestion> {
        return when (incidentType.lowercase()) {
            "physical", "sexual" -> listOf(
                ResourceSuggestion("Evidence Guide", ResourceDestination.EVIDENCE_GUIDE),
                ResourceSuggestion("Find Help Near You", ResourceDestination.FIND_HELP_NEARBY)
            )

            "verbal", "financial", "neglect" -> listOf(
                ResourceSuggestion("Understand Abuse", ResourceDestination.UNDERSTAND_ABUSE),
                ResourceSuggestion("Breaking the Stigma", ResourceDestination.BREAK_STIGMA)
            )

            else -> listOf(
                ResourceSuggestion("Understand Abuse", ResourceDestination.UNDERSTAND_ABUSE),
                ResourceSuggestion("Find Help Near You", ResourceDestination.FIND_HELP_NEARBY)
            )
        }
    }
}


