package th_prj.th_company.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import th_prj.th_company.common.BusinessException;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.notification.NotificationService;

/**
 * 게시글/댓글/첨부파일. 글 수정·삭제는 작성자 또는 관리자만 가능하다.
 */
@Service
@Transactional(readOnly = true)
public class PostService {

	public static final int PAGE_SIZE = 15;
	public static final int MAX_FILES_PER_POST = 10;
	private static final Set<String> SEARCH_FIELDS = Set.of("title", "content", "author", "all");

	private final BoardService boardService;
	private final PostRepository postRepository;
	private final CommentRepository commentRepository;
	private final AttachmentRepository attachmentRepository;
	private final MemberRepository memberRepository;
	private final FileStorage fileStorage;
	private final NotificationService notificationService;

	public PostService(BoardService boardService, PostRepository postRepository,
			CommentRepository commentRepository, AttachmentRepository attachmentRepository,
			MemberRepository memberRepository, FileStorage fileStorage, NotificationService notificationService) {
		this.boardService = boardService;
		this.postRepository = postRepository;
		this.commentRepository = commentRepository;
		this.attachmentRepository = attachmentRepository;
		this.memberRepository = memberRepository;
		this.fileStorage = fileStorage;
		this.notificationService = notificationService;
	}

	// ---------- 목록 ----------

	/** page 는 0부터. field 가 이상하면 제목+내용으로 검색한다. */
	public Page<PostSummary> list(Long boardId, String field, String keyword, int page) {
		boardService.visibleBoard(boardId);
		String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
		String f = SEARCH_FIELDS.contains(field) ? field : "all";
		return postRepository.search(boardId, f, kw, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
	}

	public List<PostSummary> pinned(Long boardId) {
		return postRepository.findPinned(boardId);
	}

	/** 홈 화면: 공지 게시판(관리자만 글쓰기)의 최근 글 */
	public List<PostLink> latestNotices(int limit) {
		return latest(limit, true);
	}

	/** 홈 화면: 공지 게시판을 뺀 나머지 게시판의 최근 글 */
	public List<PostLink> latestPosts(int limit) {
		return latest(limit, false);
	}

	private List<PostLink> latest(int limit, boolean notice) {
		List<Long> boardIds = boardService.visibleTree().stream()
			.filter(r -> !r.isFolder() && r.adminOnlyWrite() == notice)
			.map(BoardRow::id)
			.toList();
		if (boardIds.isEmpty()) {
			return List.of();
		}
		return postRepository.findLatest(boardIds, PageRequest.of(0, limit)).stream()
			.map(p -> new PostLink(p.getId(), p.getBoard().getId(), p.getBoard().getName(), p.getTitle(), p.getCreatedAt()))
			.toList();
	}

	// ---------- 상세 ----------

	/** 상세 보기 (조회수 1 증가) */
	@Transactional
	public PostDetail read(Long boardId, Long postId) {
		PostDetail detail = get(boardId, postId);
		postRepository.increaseViewCount(postId);
		return detail;
	}

	public PostDetail get(Long boardId, Long postId) {
		boardService.visibleBoard(boardId);
		Post p = findInBoard(boardId, postId);
		Member a = p.getAuthor();

		List<PostDetail.FileInfo> files = attachmentRepository.findByPostIdOrderById(postId).stream()
			.map(f -> new PostDetail.FileInfo(f.getId(), f.getOriginalName(), f.getSize()))
			.toList();
		List<PostDetail.CommentInfo> comments = commentRepository.findByPostId(postId).stream()
			.map(c -> new PostDetail.CommentInfo(c.getId(), c.getAuthor().getLoginId(), c.getAuthor().getName(),
				c.getAuthor().getDepartment() == null ? null : c.getAuthor().getDepartment().getName(),
				c.getContent(), c.getCreatedAt()))
			.toList();

		return new PostDetail(p.getId(), boardId, p.getTitle(), p.getContent(), p.isPinned(),
			a.getLoginId(), a.getName(),
			a.getDepartment() == null ? null : a.getDepartment().getName(),
			a.getGrade() == null ? null : a.getGrade().getName(),
			p.getCreatedAt(), p.getUpdatedAt(), p.getViewCount() + 1, files, comments);
	}

	/** 수정 화면용 폼 (권한 확인 포함) */
	public PostForm getForm(Long boardId, Long postId, String loginId, boolean manager) {
		Post p = findInBoard(boardId, postId);
		checkOwner(p.getAuthor().getLoginId(), loginId, manager);
		PostForm form = new PostForm();
		form.setTitle(p.getTitle());
		form.setContent(p.getContent());
		form.setPinned(p.isPinned());
		return form;
	}

	public List<PostDetail.FileInfo> attachments(Long postId) {
		return attachmentRepository.findByPostIdOrderById(postId).stream()
			.map(f -> new PostDetail.FileInfo(f.getId(), f.getOriginalName(), f.getSize()))
			.toList();
	}

	// ---------- 작성/수정/삭제 ----------

	@Transactional
	public Long create(Long boardId, PostForm form, List<MultipartFile> files, String loginId, boolean manager) {
		BoardRow board = boardService.visibleBoard(boardId);
		if (!board.canWrite(manager)) {
			throw new AccessDeniedException("이 게시판에 글을 쓸 권한이 없습니다.");
		}
		List<MultipartFile> uploads = nonEmpty(files);
		if (uploads.size() > MAX_FILES_PER_POST) {
			throw new BusinessException("첨부파일은 " + MAX_FILES_PER_POST + "개까지 올릴 수 있습니다.");
		}

		Post post = postRepository.save(new Post(boardService.reference(boardId), currentMember(loginId),
			form.getTitle().trim(), form.getContent(), manager && form.isPinned()));
		saveFiles(post, uploads);
		return post.getId();
	}

	@Transactional
	public void update(Long boardId, Long postId, PostForm form, List<MultipartFile> files,
			String loginId, boolean manager) {
		boardService.visibleBoard(boardId);
		Post post = findInBoard(boardId, postId);
		checkOwner(post.getAuthor().getLoginId(), loginId, manager);

		List<Attachment> existing = attachmentRepository.findByPostIdOrderById(postId);
		List<Attachment> toDelete = existing.stream()
			.filter(f -> form.getDeleteAttachmentIds().contains(f.getId()))
			.toList();
		List<MultipartFile> uploads = nonEmpty(files);
		if (existing.size() - toDelete.size() + uploads.size() > MAX_FILES_PER_POST) {
			throw new BusinessException("첨부파일은 " + MAX_FILES_PER_POST + "개까지 올릴 수 있습니다.");
		}

		boolean pinned = manager ? form.isPinned() : post.isPinned();
		post.update(form.getTitle().trim(), form.getContent(), pinned);
		attachmentRepository.deleteAll(toDelete);
		deleteFilesAfterCommit(toDelete);
		saveFiles(post, uploads);
	}

	@Transactional
	public void delete(Long boardId, Long postId, String loginId, boolean manager) {
		Post post = findInBoard(boardId, postId);
		checkOwner(post.getAuthor().getLoginId(), loginId, manager);

		List<Attachment> files = attachmentRepository.findByPostIdOrderById(postId);
		attachmentRepository.deleteAll(files);
		commentRepository.deleteByPostId(postId);
		postRepository.delete(post);
		deleteFilesAfterCommit(files);
	}

	// ---------- 댓글 ----------

	@Transactional
	public void addComment(Long boardId, Long postId, String content, String loginId) {
		boardService.visibleBoard(boardId);
		if (content == null || content.isBlank()) {
			throw new BusinessException("댓글 내용을 입력하세요.");
		}
		if (content.length() > 1000) {
			throw new BusinessException("댓글은 1000자 이하로 입력하세요.");
		}
		Post post = findInBoard(boardId, postId);
		Member me = currentMember(loginId);
		commentRepository.save(new Comment(post, me, content.trim()));
		if (!post.getAuthor().getId().equals(me.getId())) {
			notificationService.send(post.getAuthor(), "게시판", me.getName() + " 님이 '" + post.getTitle() + "' 글에 댓글을 남겼습니다.",
				"/boards/" + boardId + "/posts/" + postId + "#comments");
		}
	}

	@Transactional
	public void deleteComment(Long boardId, Long postId, Long commentId, String loginId, boolean manager) {
		Comment comment = commentRepository.findById(commentId)
			.filter(c -> c.getPost().getId().equals(postId) && c.getPost().getBoard().getId().equals(boardId))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		checkOwner(comment.getAuthor().getLoginId(), loginId, manager);
		commentRepository.delete(comment);
	}

	// ---------- 첨부파일 ----------

	public record Download(String name, String contentType, long size, Resource resource) {
	}

	public Download download(Long attachmentId) {
		Attachment f = attachmentRepository.findWithBoard(attachmentId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		boardService.visibleBoard(f.getPost().getBoard().getId());
		Resource resource = fileStorage.load(f.getStoredName());
		if (!resource.exists()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return new Download(f.getOriginalName(), f.getContentType(), f.getSize(), resource);
	}

	// ---------- 내부 ----------

	private void saveFiles(Post post, List<MultipartFile> uploads) {
		for (MultipartFile file : uploads) {
			String storedName = fileStorage.store(file);
			attachmentRepository.save(new Attachment(post, originalName(file), storedName,
				file.getSize(), file.getContentType()));
		}
	}

	/** DB 커밋이 끝난 뒤에 실제 파일을 지운다 (롤백되면 파일 유지). */
	private void deleteFilesAfterCommit(List<Attachment> files) {
		if (files.isEmpty()) {
			return;
		}
		List<String> names = files.stream().map(Attachment::getStoredName).toList();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				names.forEach(fileStorage::delete);
			}
		});
	}

	private static List<MultipartFile> nonEmpty(List<MultipartFile> files) {
		if (files == null) {
			return List.of();
		}
		return new ArrayList<>(files.stream().filter(f -> f != null && !f.isEmpty()).toList());
	}

	/** 일부 브라우저는 전체 경로를 보내므로 파일명만 남긴다. */
	private static String originalName(MultipartFile file) {
		String name = StringUtils.getFilename(StringUtils.cleanPath(
			file.getOriginalFilename() == null ? "file" : file.getOriginalFilename().replace('\\', '/')));
		if (name == null || name.isBlank()) {
			name = "file";
		}
		return name.length() > 255 ? name.substring(name.length() - 255) : name;
	}

	private Post findInBoard(Long boardId, Long postId) {
		return postRepository.findDetail(postId)
			.filter(p -> p.getBoard().getId().equals(boardId))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private Member currentMember(String loginId) {
		return memberRepository.findByLoginId(loginId)
			.orElseThrow(() -> new AccessDeniedException("사용자 정보를 찾을 수 없습니다."));
	}

	/** manager: 관리자 또는 게시판관리자 (남의 글·댓글도 수정·삭제, 공지 쓰기, 상단 고정) */
	private static void checkOwner(String ownerLoginId, String loginId, boolean manager) {
		if (!manager && !ownerLoginId.equals(loginId)) {
			throw new AccessDeniedException("작성자만 수정하거나 삭제할 수 있습니다.");
		}
	}
}
