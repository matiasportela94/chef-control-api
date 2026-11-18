package com.chefcontrol.api.ai.dto;

import com.chefcontrol.application.service.ChatService;

import java.util.List;

/**
 * Una alternativa de un NEEDS_CONFIRMATION: lo que confirma su botón (con /ai/chat/confirm).
 *
 * @param label lo arma el back: "Por kg · $ 750.000" / "Total · $ 30.000 por kg" con un precio de dos lecturas,
 *              "Confirmar" si no
 */
public record ChatProposal(String label, List<ChatOperation> operations) {

    static ChatProposal from(ChatService.Proposal p) {
        return new ChatProposal(p.label(), p.operations().stream().map(ChatOperation::from).toList());
    }
}
