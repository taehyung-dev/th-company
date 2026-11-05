package th_prj.th_company;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LoginTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void loginPageIsPublic() throws Exception {
		mvc.perform(get("/login")).andExpect(status().isOk());
	}

	@Test
	void mainRequiresLogin() throws Exception {
		mvc.perform(get("/"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	void sysadminCanLogin() throws Exception {
		mvc.perform(formLogin("/login").user("sysadmin").password("a"))
			.andExpect(authenticated().withUsername("sysadmin"))
			.andExpect(redirectedUrl("/"));
	}

	@Test
	void wrongPasswordFails() throws Exception {
		mvc.perform(formLogin("/login").user("sysadmin").password("wrong"))
			.andExpect(unauthenticated())
			.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	void mainRendersAfterLogin() throws Exception {
		MockHttpSession session = (MockHttpSession) mvc
			.perform(formLogin("/login").user("sysadmin").password("a"))
			.andReturn().getRequest().getSession();

		mvc.perform(get("/").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<strong>시스템관리자</strong> 님")));
	}
}
