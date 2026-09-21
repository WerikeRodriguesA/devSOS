package com.devsos.application.service;

import com.devsos.application.dto.user.AtualizarPerfilRequestDTO;
import com.devsos.application.exception.RegraDeNegocioException;
import com.devsos.application.exception.ResourceNotFoundException;
import com.devsos.domain.user.UserEntity;
import com.devsos.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private final UUID id = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private UserEntity usuario() {
        UserEntity usuario = new UserEntity("Ana Dev", "ana@dev.com", "anadev", "hash");
        usuario.setBio("bio antiga");
        usuario.setAvatarUrl("http://localhost:8080/api/uploads/antigo.png");
        usuario.setTecnologiasDominadas(List.of("java"));
        return usuario;
    }

    @Test
    void atualizarPerfilAplicaTodosOsCamposInformados() {
        UserEntity usuario = usuario();
        when(userRepository.findById(id)).thenReturn(Optional.of(usuario));
        var request = new AtualizarPerfilRequestDTO(
            "Ana Renovada",
            "  Minha nova bio  ",
            "ana-dev-nova",
            "https://github.com/ana.png",
            List.of("  Java ", " Spring ", "SQL")
        );

        var resultado = userService.atualizarPerfil(id, request);

        assertThat(resultado.nome()).isEqualTo("Ana Renovada");
        assertThat(usuario.getNome()).isEqualTo("Ana Renovada");
        assertThat(usuario.getBio()).isEqualTo("Minha nova bio");
        assertThat(usuario.getGithubUsername()).isEqualTo("ana-dev-nova");
        assertThat(usuario.getAvatarUrl()).isEqualTo("https://github.com/ana.png");
        assertThat(usuario.getTecnologiasDominadas())
            .containsExactly("java", "spring", "sql");
        verify(userRepository).saveAndFlush(usuario);
    }

    @Test
    void camposNulosNaoAlteramNada() {
        UserEntity usuario = usuario();
        when(userRepository.findById(id)).thenReturn(Optional.of(usuario));

        userService.atualizarPerfil(id, new AtualizarPerfilRequestDTO(
            null, null, null, null, null));

        assertThat(usuario.getNome()).isEqualTo("Ana Dev");
        assertThat(usuario.getBio()).isEqualTo("bio antiga");
        assertThat(usuario.getAvatarUrl()).isEqualTo("http://localhost:8080/api/uploads/antigo.png");
        verify(userRepository, never()).existsByGithubUsernameAndIdNot(any(), any());
    }

    @Test
    void stringVaziaLimpaCampo() {
        UserEntity usuario = usuario();
        when(userRepository.findById(id)).thenReturn(Optional.of(usuario));
        var request = new AtualizarPerfilRequestDTO(
            null, null, "", "", null);

        userService.atualizarPerfil(id, request);

        assertThat(usuario.getAvatarUrl()).isEmpty();
        assertThat(usuario.getGithubUsername()).isEmpty();
        verify(userRepository, never()).existsByGithubUsernameAndIdNot(any(), any());
    }

    @Test
    void githubDeOutroUsuarioLancaRegraDeNegocio() {
        UserEntity usuario = usuario();
        when(userRepository.findById(id)).thenReturn(Optional.of(usuario));
        when(userRepository.existsByGithubUsernameAndIdNot("ocupado", id)).thenReturn(true);

        assertThatThrownBy(() -> userService.atualizarPerfil(
            id, new AtualizarPerfilRequestDTO(null, null, "ocupado", null, null)))
            .isInstanceOf(RegraDeNegocioException.class)
            .hasMessage("Este GitHub username já está em uso.");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void manterOGithubAtualDoUsuarioEhPermitido() {
        UserEntity usuario = usuario();
        when(userRepository.findById(id)).thenReturn(Optional.of(usuario));
        when(userRepository.existsByGithubUsernameAndIdNot("anadev", id)).thenReturn(false);

        userService.atualizarPerfil(
            id, new AtualizarPerfilRequestDTO(null, null, "anadev", null, null));

        assertThat(usuario.getGithubUsername()).isEqualTo("anadev");
        verify(userRepository).saveAndFlush(usuario);
    }

    @Test
    void tecnologiasSaoNormalizadasEMaximasValidadasNoDto() {
        UserEntity usuario = usuario();
        when(userRepository.findById(id)).thenReturn(Optional.of(usuario));
        var request = new AtualizarPerfilRequestDTO(
            null, null, null, null, List.of("  Clean Code ", "TDD"));

        userService.atualizarPerfil(id, request);

        assertThat(usuario.getTecnologiasDominadas())
            .containsExactly("clean code", "tdd");
    }

    @Test
    void usuarioInexistenteLancaResourceNotFound() {
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.atualizarPerfil(
            id, new AtualizarPerfilRequestDTO(null, null, null, null, null)))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}