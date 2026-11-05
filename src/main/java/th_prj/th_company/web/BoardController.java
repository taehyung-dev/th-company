package th_prj.th_company.web;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import th_prj.th_company.board.BoardRow;
import th_prj.th_company.board.BoardService;
import th_prj.th_company.board.PostForm;
import th_prj.th_company.board.PostService;
import th_prj.th_company.board.PostSummary;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.common.SecurityUtils;
import th_prj.th_company.member.Permission;

/**
 * 사용자 게시판: 좌측 게시판 트리 + 우측 글 목록/상세/작성.
 */
@Controller
public class BoardController {

	private final BoardService boardService;
	private final PostService postService;

	public BoardController(BoardService boardService, PostService postService) {
		this.boardService = boardService;
		this.postService = postService;
	}

	/** 첫 번째 게시판으로 이동 */
	@GetMapping("/boards")
	public String index(Model model) {
		List<BoardRow> tree = boardService.visibleTree();
		return tree.stream()
			.filter(r -> !r.isFolder())
			.findFirst()
			.map(r -> "redirect:/boards/" + r.id())
			.orElseGet(() -> {
				model.addAttribute("boards", tree);
				return "board/empty";
			});
	}

	/** @param page 1부터 */
	@GetMapping("/boards/{boardId}")
	public String list(@PathVariable Long boardId,
			@RequestParam(defaultValue = "all") String field,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "1") int page,
			Authentication authentication, Model model) {
		prepare(boardId, authentication, model);
		Page<PostSummary> posts = postService.list(boardId, field, q, page - 1);
		boolean searching = q != null && !q.isBlank();

		model.addAttribute("pinned", searching || page > 1 ? List.of() : postService.pinned(boardId));
		model.addAttribute("posts", posts);
		model.addAttribute("field", field);
		model.addAttribute("q", q);
		model.addAttribute("pages", pageNumbers(posts));
		return "board/list";
	}

	@GetMapping("/boards/{boardId}/posts/{postId}")
	public String view(@PathVariable Long boardId, @PathVariable Long postId,
			Authentication authentication, Model model) {
		prepare(boardId, authentication, model);
		model.addAttribute("post", postService.read(boardId, postId));
		return "board/view";
	}

	// ---------- 작성 ----------

	@GetMapping("/boards/{boardId}/posts/new")
	public String createForm(@PathVariable Long boardId, Authentication authentication, Model model) {
		model.addAttribute("form", new PostForm());
		return renderForm(boardId, null, authentication, model);
	}

	@PostMapping("/boards/{boardId}/posts")
	public String create(@PathVariable Long boardId,
			@Valid @ModelAttribute("form") PostForm form, BindingResult bindingResult,
			@RequestParam(name = "uploads", required = false) List<MultipartFile> uploads,
			Authentication authentication, Model model, RedirectAttributes redirect) {
		if (bindingResult.hasErrors()) {
			return renderForm(boardId, null, authentication, model);
		}
		try {
			Long postId = postService.create(boardId, form, uploads, authentication.getName(),
				SecurityUtils.can(authentication, Permission.BOARD_MANAGER));
			return "redirect:/boards/" + boardId + "/posts/" + postId;
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return renderForm(boardId, null, authentication, model);
		}
	}

	// ---------- 수정 ----------

	@GetMapping("/boards/{boardId}/posts/{postId}/edit")
	public String editForm(@PathVariable Long boardId, @PathVariable Long postId,
			Authentication authentication, Model model) {
		model.addAttribute("form", postService.getForm(boardId, postId, authentication.getName(),
			SecurityUtils.can(authentication, Permission.BOARD_MANAGER)));
		return renderForm(boardId, postId, authentication, model);
	}

	@PostMapping("/boards/{boardId}/posts/{postId}")
	public String update(@PathVariable Long boardId, @PathVariable Long postId,
			@Valid @ModelAttribute("form") PostForm form, BindingResult bindingResult,
			@RequestParam(name = "uploads", required = false) List<MultipartFile> uploads,
			Authentication authentication, Model model, RedirectAttributes redirect) {
		if (bindingResult.hasErrors()) {
			return renderForm(boardId, postId, authentication, model);
		}
		try {
			postService.update(boardId, postId, form, uploads, authentication.getName(),
				SecurityUtils.can(authentication, Permission.BOARD_MANAGER));
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return renderForm(boardId, postId, authentication, model);
		}
		redirect.addFlashAttribute("message", "글을 수정했습니다.");
		return "redirect:/boards/" + boardId + "/posts/" + postId;
	}

	@PostMapping("/boards/{boardId}/posts/{postId}/delete")
	public String delete(@PathVariable Long boardId, @PathVariable Long postId,
			Authentication authentication, RedirectAttributes redirect) {
		postService.delete(boardId, postId, authentication.getName(), SecurityUtils.can(authentication, Permission.BOARD_MANAGER));
		redirect.addFlashAttribute("message", "글을 삭제했습니다.");
		return "redirect:/boards/" + boardId;
	}

	// ---------- 댓글 ----------

	@PostMapping("/boards/{boardId}/posts/{postId}/comments")
	public String addComment(@PathVariable Long boardId, @PathVariable Long postId,
			@RequestParam String content, Authentication authentication, RedirectAttributes redirect) {
		try {
			postService.addComment(boardId, postId, content, authentication.getName());
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/boards/" + boardId + "/posts/" + postId + "#comments";
	}

	@PostMapping("/boards/{boardId}/posts/{postId}/comments/{commentId}/delete")
	public String deleteComment(@PathVariable Long boardId, @PathVariable Long postId, @PathVariable Long commentId,
			Authentication authentication) {
		postService.deleteComment(boardId, postId, commentId, authentication.getName(),
			SecurityUtils.can(authentication, Permission.BOARD_MANAGER));
		return "redirect:/boards/" + boardId + "/posts/" + postId + "#comments";
	}

	// ---------- 첨부파일 ----------

	@GetMapping("/attachments/{id}")
	public ResponseEntity<Resource> download(@PathVariable Long id) {
		PostService.Download file = postService.download(id);
		MediaType type = file.contentType() == null
			? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(file.contentType());
		return ResponseEntity.ok()
			.contentType(type)
			.contentLength(file.size())
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
				.filename(file.name(), StandardCharsets.UTF_8).build().toString())
			.body(file.resource());
	}

	// ---------- 내부 ----------

	/** 좌측 트리와 현재 게시판을 모델에 넣는다. */
	private BoardRow prepare(Long boardId, Authentication authentication, Model model) {
		BoardRow board = boardService.visibleBoard(boardId);
		model.addAttribute("boards", boardService.visibleTree());
		model.addAttribute("board", board);
		model.addAttribute("canWrite", board.canWrite(SecurityUtils.can(authentication, Permission.BOARD_MANAGER)));
		return board;
	}

	private String renderForm(Long boardId, Long postId, Authentication authentication, Model model) {
		BoardRow board = prepare(boardId, authentication, model);
		if (postId == null) {
			requireWritable(board, authentication);
		}
		model.addAttribute("postId", postId);
		model.addAttribute("files", postId == null ? List.of() : postService.attachments(postId));
		model.addAttribute("maxFiles", PostService.MAX_FILES_PER_POST);
		return "board/form";
	}

	private static void requireWritable(BoardRow board, Authentication authentication) {
		if (!board.canWrite(SecurityUtils.can(authentication, Permission.BOARD_MANAGER))) {
			throw new AccessDeniedException("이 게시판에 글을 쓸 권한이 없습니다.");
		}
	}

	/** 현재 페이지 앞뒤로 최대 10개 페이지 번호 (1부터) */
	private static List<Integer> pageNumbers(Page<?> page) {
		int total = Math.max(page.getTotalPages(), 1);
		int current = page.getNumber() + 1;
		int start = Math.max(1, current - 4);
		int end = Math.min(total, start + 9);
		start = Math.max(1, end - 9);
		return IntStream.rangeClosed(start, end).boxed().toList();
	}
}
