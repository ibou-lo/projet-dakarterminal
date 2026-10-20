package sn.dakarterminal.autotrack.assistant;

import sn.dakarterminal.autotrack.security.AuthPrincipal;
import sn.dakarterminal.autotrack.web.dto.Dtos.ChatReply;
import sn.dakarterminal.autotrack.web.dto.Dtos.ChatRequest;

/**
 * Moteur de l'assistant. Le prototype fournit un moteur à règles
 * ({@link RuleBasedAssistantEngine}) qui répond uniquement à partir des données
 * enregistrées. Un moteur LLM peut être branché en implémentant cette interface :
 * il devra recevoir les mêmes données (outils de lecture) et ne jamais exécuter
 * d'action sans passer par {@link AssistantService#confirm}.
 */
public interface AssistantEngine {

    ChatReply reply(ChatRequest request, AuthPrincipal user);
}
