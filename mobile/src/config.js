/**
 * config.js
 * ---------------------------------------------------------------------------
 * O "application.properties" do app: centraliza tudo que muda por ambiente.
 *
 * ANALOGIA (backend):
 * - No Spring você configura `server.port` e o endereço do banco num arquivo
 *   de propriedades. Aqui o análogo é este arquivo: uma constante que informa
 *   ONDE o backend mora.
 *
 * POR QUE NÃO USAR "localhost"?
 * - "localhost" no CELULAR é o próprio celular (não o seu PC). Quando o app
 *   rodar no aparelho/emulador, ele precisa do IP da MÁQUINA na rede Wi-Fi.
 * - Troque pelo IP do seu computador (veja docs/TROUBLESHOOTING_REDE.md).
 * - No emulador Android, use 10.0.2.2 (alias para o localhost da máquina).
 */
export const API_BASE_URL = 'http://192.168.0.10:8080';

/**
 * O WebSocket (SockJS) é servido pelo MESMO Spring Boot, então usa a mesma
 * base. O caminho /ws-devsos é adicionado pelo service socket.js.
 */
export const WS_BASE_URL = API_BASE_URL;