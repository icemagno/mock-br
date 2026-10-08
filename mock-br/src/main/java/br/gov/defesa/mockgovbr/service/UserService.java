package br.gov.defesa.mockgovbr.service;

import br.gov.defesa.mockgovbr.model.MockUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final Map<String, MockUser> userMap = new ConcurrentHashMap<>();

    @Value("${mock.users-file:classpath:users.json}")
    private String usersFilePath;

    public UserService(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadUsers() {
        try {
            log.info("Carregando base de usuários mockados a partir de: {}", usersFilePath);
            Resource resource = resourceLoader.getResource(usersFilePath);
            try (InputStream is = resource.getInputStream()) {
                List<MockUser> users = objectMapper.readValue(is, new TypeReference<>() {});
                userMap.clear();
                for (MockUser u : users) {
                    userMap.put(u.cleanCpf(), u);
                }
                log.info("Base carregada com sucesso. Total de usuários mockados: {}", userMap.size());
            }
        } catch (Exception e) {
            log.error("Erro ao carregar usuários mockados de {}: {}", usersFilePath, e.getMessage(), e);
        }
    }

    public List<MockUser> listAll() {
        return new ArrayList<>(userMap.values());
    }

    public Optional<MockUser> authenticate(String cpf, String password) {
        if (cpf == null || password == null) {
            return Optional.empty();
        }
        String cleanCpf = cpf.replaceAll("\\D", "");
        MockUser user = userMap.get(cleanCpf);
        if (user != null && user.password().equals(password.trim())) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public Optional<MockUser> findByCpf(String cpf) {
        if (cpf == null) return Optional.empty();
        return Optional.ofNullable(userMap.get(cpf.replaceAll("\\D", "")));
    }
}
