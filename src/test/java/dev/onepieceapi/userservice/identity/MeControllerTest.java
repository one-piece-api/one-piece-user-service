package dev.onepieceapi.userservice.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
class MeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsEmailAndRolesFromTheToken() throws Exception {
		var adminAndEditor = new SimpleGrantedAuthority[] { new SimpleGrantedAuthority("ROLE_ADMIN"),
				new SimpleGrantedAuthority("ROLE_EDITOR") };
		var asLuffy = jwt().jwt(jwt -> jwt.claim("email", "luffy@onepiece.local")).authorities(adminAndEditor);

		this.mockMvc.perform(get("/me").with(asLuffy)).andExpect(status().isOk()).andExpect(content().json("""
				{"email": "luffy@onepiece.local", "roles": ["ADMIN", "EDITOR"]}
				"""));
	}

}
