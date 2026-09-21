package com.devsos.infrastructure.storage;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Cria o bucket no boot se ele ainda não existir.
 *
 * <p>O padrão de "bootstrap idempotente" evita tarefa manual (criar bucket na
 * mão toda vez que sobe um container novo) e dá o mesmo tratamento que damos
 * às migrações do banco: o ambiente fica SEMPRE no estado esperado pelo
 * código. Só roda quando o storage está configurado.</p>
 */
@Component
@ConditionalOnExpression("'${devsos.storage.endpoint:}' != ''")
public class StorageBootstrap implements ApplicationRunner {

    private final StorageService storageService;

    public StorageBootstrap(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public void run(ApplicationArguments args) {
        storageService.criarBucketSeNecessario();
    }
}