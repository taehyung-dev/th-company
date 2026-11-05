package th_prj.th_company.file;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import th_prj.th_company.board.FileStorage;
import th_prj.th_company.board.PostDetail.FileInfo;
import th_prj.th_company.board.PostService;
import th_prj.th_company.common.BusinessException;

/**
 * 전자결재·일정·예약 공통 첨부파일. 볼 권한은 FileController 가 대상별로 확인한다.
 * DB 와 디스크를 맞추기 위해, 지운 파일은 커밋 뒤에 지우고 새로 올린 파일은 롤백되면 지운다.
 */
@Service
@Transactional(readOnly = true)
public class FileAttachmentService {

	public static final int MAX_FILES = PostService.MAX_FILES_PER_POST;

	private final FileAttachmentRepository repository;
	private final FileStorage fileStorage;

	public FileAttachmentService(FileAttachmentRepository repository, FileStorage fileStorage) {
		this.repository = repository;
		this.fileStorage = fileStorage;
	}

	public record Download(FileOwner ownerType, Long ownerId, String name, String contentType, long size, Resource resource) {
	}

	public List<FileInfo> list(FileOwner owner, Long ownerId) {
		return repository.findByOwnerTypeAndOwnerIdOrderById(owner, ownerId).stream()
			.map(f -> new FileInfo(f.getId(), f.getOriginalName(), f.getSize()))
			.toList();
	}

	public Download download(Long id) {
		FileAttachment f = repository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		Resource resource = fileStorage.load(f.getStoredName());
		if (!resource.exists()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return new Download(f.getOwnerType(), f.getOwnerId(), f.getOriginalName(), f.getContentType(), f.getSize(), resource);
	}

	/** deleteIds 의 파일을 지우고 uploads 를 올린다. 그 대상에 달린 파일만 지운다 */
	@Transactional
	public void sync(FileOwner owner, Long ownerId, List<MultipartFile> uploads, List<Long> deleteIds) {
		List<FileAttachment> existing = repository.findByOwnerTypeAndOwnerIdOrderById(owner, ownerId);
		List<FileAttachment> toDelete = deleteIds == null ? List.of()
			: existing.stream().filter(f -> deleteIds.contains(f.getId())).toList();
		List<MultipartFile> files = nonEmpty(uploads);
		if (existing.size() - toDelete.size() + files.size() > MAX_FILES) {
			throw new BusinessException("첨부파일은 " + MAX_FILES + "개까지 올릴 수 있습니다.");
		}
		remove(toDelete);
		List<String> stored = new ArrayList<>();
		deleteIfRolledBack(stored);
		for (MultipartFile file : files) {
			String storedName = fileStorage.store(file);
			stored.add(storedName);
			repository.save(new FileAttachment(owner, ownerId, originalName(file), storedName, file.getSize(), file.getContentType()));
		}
	}

	@Transactional
	public void deleteAll(FileOwner owner, Long ownerId) {
		remove(repository.findByOwnerTypeAndOwnerIdOrderById(owner, ownerId));
	}

	private void remove(List<FileAttachment> files) {
		if (files.isEmpty()) {
			return;
		}
		repository.deleteAll(files);
		List<String> names = files.stream().map(FileAttachment::getStoredName).toList();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				names.forEach(fileStorage::delete);
			}
		});
	}

	private void deleteIfRolledBack(List<String> stored) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					stored.forEach(fileStorage::delete);
				}
			}
		});
	}

	private static List<MultipartFile> nonEmpty(List<MultipartFile> files) {
		return files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
	}

	private static String originalName(MultipartFile file) {
		String raw = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename().replace('\\', '/');
		String name = StringUtils.getFilename(StringUtils.cleanPath(raw));
		if (name == null || name.isBlank()) {
			name = "file";
		}
		return name.length() > 255 ? name.substring(name.length() - 255) : name;
	}
}
