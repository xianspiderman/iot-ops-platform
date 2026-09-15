package io.github.xianspiderman.iotops.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SysUserJsonTest {
    @Test
    void passwordHashIsNeverSerialized() throws Exception {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("secret-hash");

        String json = new ObjectMapper().writeValueAsString(user);

        assertThat(json).contains("admin").doesNotContain("passwordHash", "secret-hash");
    }
}
