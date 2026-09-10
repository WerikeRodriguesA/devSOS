# DevSOS Mobile — Guia de Troubleshooting de Rede

O problema Nº 1 de quem sobe um app mobile é: **o app não consegue falar com
o backend que roda na máquina**. A causa, quase sempre, é uma só palavra:
`localhost`.

---

## 1. Por que "localhost" não funciona?

- **No seu PC**: `localhost` aponta para o próprio PC.
- **No celular / emulador**: `localhost` aponta para o PRÓPRIO celular.

Ou seja: se o `config.js` está com `http://localhost:8080`, o app está pedindo
comida para a própria cozinha do restaurante dele — que não tem nada. O backend
vive no **seu computador**, e o app precisa do **endereço do seu computador na
rede**, não de "local".

```js
// ERRADO
export const API_BASE_URL = 'http://localhost:8080';

// CERTO (IP do seu PC na rede Wi-Fi)
export const API_BASE_URL = 'http://192.168.0.10:8080';
```

> Única exceção: no **emulador Android**, o alias `10.0.2.2` foi criado
> justamente para apontar ao localhost da MÁQUINA. Nesse caso use:
> `http://10.0.2.2:8080`.

---

## 2. Descobrindo o IP da sua máquina

| Sistema | Comando |
|---------|---------|
| Windows | `ipconfig` → procurar endereço IPv4 do adaptador Wi-Fi |
| Linux | `ip addr` ou `ifconfig` |
| macOS | `ifconfig` ou `ipconfig getifaddr en0` |

Você busca o IP **da placa Wi-Fi** (ex.: `192.168.0.10`), não o `127.0.0.1`
(loopback) nem o IP de `docker0`/VPN.

---

## 3. Testando se o backend está acessível

Primeiro do próprio PC:

```bash
curl http://localhost:8080/ws-devsos/info
# esperado: {"websocket":true,...}
```

Depois, teste como o CELULAR enxergaria (substitua o IP):

```bash
curl http://192.168.0.10:8080/ws-devsos/info
```

Se funcionar do PC mas não pelo IP, siga a próxima seção.

---

## 4. Checklist dos 5 C's da conectividade local

1. **Mesma rede** — o celular e o PC precisam estar no mesmo Wi-Fi (e o Wi-Fi
   sem "isolamento de cliente"/AP isolation, comum em redes de condomínio).
2. **Backend escutando em todas interfaces** — o Spring Boot por padrão
   escuta em `0.0.0.0:8080` (todas as interfaces). Se você mudou
   `server.address`, troque para o IP da máquina.
3. **Firewall** — libere a porta `8080` para a rede privada:
   - Windows: `netsh advfirewall firewall add rule name="DevSOS 8080" dir=in action=allow protocol=TCP localport=8080`.
   - Linux: `sudo ufw allow 8080/tcp`.
4. **Porta não conflitante** — `netstat -ano | findstr :8080` (Windows) deve
   mostrar o processo do Java (`restarted`, PID do spring-boot).
5. **CORS (só importa no Expo Web)** — no app nativo não há navegador, não há
   CORS. Se você testar no navegador (Expo Web), o backend já libera com
   `setAllowedOriginPatterns("*")` no WebSocket e permite CORS no REST dev.

---

## 5. WebSocket / STOMP no React Native

- O `socket.js` força o transport `websocket` do SockJS
  (`new SockJS(url, null, { transports: ['websocket'] })`). Sem isso, o
  SockJS tentaria transports baseados em XHR que **não existem** no RN.
- O token JWT vai na **query string** (`?token=...`): o backend
  (`JwtWebSocketHandshakeInterceptor`) lê de lá ou do header `Authorization`.
- Se o SockJS reclamar de `XMLHttpRequest`/`events`/`self` no Expo, o
  `socket.js` já embute o polyfill mínimo de `self`. Se ainda assim falhar
  no seu run (varia por versão), remova `sockjs-client` e conecte com o
  WebSocket nativo do RN diretamente na URL raw
  (`ws://IP:8080/ws-devsos/000/<aleatorio>/websocket?token=...`) e use
  `@stomp/stompjs` com `webSocketFactory`.

---

## 6. Dica extra: `adb reverse` para testes com USB (Android)

Se o celular estiver conectado por **USB com depuração ativada**, você pode
"espelhar" a porta 8080 do PC sem mexer em firewall:

```bash
adb reverse tcp:8080 tcp:8080
```

Depois disso, `http://localhost:8080` no app funciona no celular físico
(nada de trocar IP). Perfeito para desenvolvimento rápido. A desvantagem:
funciona só enquanto o cabo estiver conectado.

---

## 7. Resumo rápido do fluxo de diagnóstico

```
1. App abre?  -> Não: problema de Expo/Metro (rode `npx expo start` no terminal)
2. Login falha com "Servidor indisponível"? -> rede (IP/firewall/mesma rede)
3. Login ok mas chat cai? -> WebSocket (token na URL + transport websocket)
4. Feed vazio mas sem erro? -> API devolvendo page sem content (posts OPEN zerados)
```

O backend loga tudo em `devsos-app.log`. Um erro no SDK do cliente nunca some
do log — sempre verifique os dois lados (app E backend).