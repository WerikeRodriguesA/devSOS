package com.devsos.application.service;

import com.devsos.application.dto.user.AtualizarTecnologiasRequestDTO;
import com.devsos.application.dto.user.UserProfileResponseDTO;
import com.devsos.application.exception.ResourceNotFoundException;
import com.devsos.domain.user.UserEntity;
import com.devsos.infrastructure.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Serviço de usuários: contém a REGRA DE NEGÓCIO do perfil.
 *
 * <h2>Por que INJEÇÃO DE DEPENDÊNCIA VIA CONSTRUTOR (e não @Autowired em
 * atributos)?</h2>
 * <ul>
 *   <li><b>Imutabilidade:</b> o campo {@code repository} é {@code final} —
 *       uma vez setado no construtor, nunca muda. Código mais previsível.</li>
 *   <li><b>Testabilidade:</b> para testar, basta passar um repositório fake
 *       no construtor. Com {@code @Autowired} em atributo privado, o teste
 *       precisaria de reflexão ou do container inteiro.</li>
 *   <li><b>Falha cedo:</b> se faltar uma dependência, o construtor QUEBRA na
 *       inicialização (fail fast), em vez de {@code NullPointerException}
 *       escondida em runtime.</li>
 * </ul>
 * <p>O Spring já entende um único construtor público como ponto de injeção —
 * nenhuma anotação adicional é necessária (isso é o padrão moderno).
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Busca o perfil público do usuário.
     * {@code orElseThrow()} converte {@code Optional} vazio em
     * {@link ResourceNotFoundException} → o handler devolve HTTP 404.
     * <p>
     * {@code @Transactional(readOnly = true)}: otimiza a conexão e comunica
     * intenção (não modifica nada). Boa prática para qualquer READ duro.
     */
    @Transactional(readOnly = true)
    public UserProfileResponseDTO buscarPerfilPorId(UUID id) {
        UserEntity user = userRepository.findById(id)
            .orElseThrow(() -> ResourceNotFoundException.of("Usuário"));

        return UserProfileResponseDTO.from(user);
    }

    /**
     * ROTA DE NEGÓCIO: atualiza a lista de tecnologias dominadas do dev.
     * Repare que o Service controla a ENTIDADE e o Repository apenas PERSISTE;
     * a regra aqui é "o que esse perfil declara dominar" (sem apagar outros
     * dados do usuário).
     * <p>{@code saveAndFlush()} força a gravação imediata e devolve a entidade
     * com os timestamps que o banco calculou (defaults/triggers) — essencial
     * porque os campos de tempo são {@code insertable=false/updatable=false}.</p>
     */
    @Transactional
    public UserProfileResponseDTO atualizarTecnologias(UUID id, AtualizarTecnologiasRequestDTO request) {
        UserEntity user = userRepository.findById(id)
            .orElseThrow(() -> ResourceNotFoundException.of("Usuário"));

        List<String> tecnologias = request.tecnologiasDominadas()
            .stream()
            .map(String::trim)
            .map(s -> s.toLowerCase(java.util.Locale.ROOT))
            .toList();

        user.setTecnologiasDominadas(tecnologias);
        userRepository.saveAndFlush(user);

        return UserProfileResponseDTO.from(user);
    }
}