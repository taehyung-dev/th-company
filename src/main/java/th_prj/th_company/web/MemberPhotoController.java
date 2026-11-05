package th_prj.th_company.web;

import java.time.Duration;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import th_prj.th_company.common.SecurityUtils;
import th_prj.th_company.member.ProfilePhotoService;

/** 프로필 사진. 로그인한 사용자만 볼 수 있다 */
@Controller
public class MemberPhotoController {

	private final ProfilePhotoService profilePhotoService;

	public MemberPhotoController(ProfilePhotoService profilePhotoService) {
		this.profilePhotoService = profilePhotoService;
	}

	/** 사진을 바꾸면 주소(?v=)가 바뀌므로 브라우저에 오래 캐시해도 된다 */
	@GetMapping("/members/{id}/photo")
	public ResponseEntity<Resource> photo(@PathVariable Long id, Authentication authentication) {
		return profilePhotoService.load(id, SecurityUtils.isAdmin(authentication))
			.map(r -> ResponseEntity.ok()
				.contentType(MediaType.IMAGE_JPEG)
				.cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
				.body(r))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
