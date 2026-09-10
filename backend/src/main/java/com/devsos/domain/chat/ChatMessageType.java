package com.devsos.domain.chat;

/**
 * Tipos de mensagem da sala de chat.
 *
 * <ul>
 *   <li><b>{@code CHAT}</b> — mensagem de texto livre (o comum da conversa);</li>
 *   <li><b>{@code CODE_SNIPPET}</b> — trecho de código/stacktrace (o front pode
 *       renderizar em caixa de código);</li>
 *   <li><b>{@code JOIN}/{@code LEAVE}</b> — avisos do sistema quando alguém entra
 *       ou sai da sala. Identificam o participante em {@code content} (ex.:
 *       "Diana Java entrou na sala"). Não são persistidas no histórico.</li>
 * </ul>
 */
public enum ChatMessageType {
    CHAT,
    CODE_SNIPPET,
    JOIN,
    LEAVE
}