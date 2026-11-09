package th_prj.th_company.board;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 첨부파일을 로컬 디스크(app.upload-dir)에 저장한다.
 * 저장 이름은 "yyyy/MM/uuid" 로 원본 파일명과 무관하다.
 */
@Component
public class FileStorage {

	private final Path root;

	public FileStorage(@Value("${app.upload-dir}") String uploadDir) {
		this.root = Path.of(uploadDir).toAbsolutePath().normalize();
	}

	public String store(MultipartFile file) {
		String storedName = newName();
		Path target = resolve(storedName);
		try {
			Files.createDirectories(target.getParent());
			file.transferTo(target);
		} catch (IOException e) {
			throw new UncheckedIOException("첨부파일 저장 실패", e);
		}
		return storedName;
	}

	/** 서버에서 만든 내용(변환한 프로필 사진 등)을 저장한다 */
	public String store(byte[] content) {
		String storedName = newName();
		Path target = resolve(storedName);
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, content);
		} catch (IOException e) {
			throw new UncheckedIOException("파일 저장 실패", e);
		}
		return storedName;
	}

	/** 같은 내용의 새 파일을 만든다 (문서마다 첨부파일을 따로 지울 수 있게) */
	public String copy(String storedName) {
		String copied = newName();
		Path target = resolve(copied);
		try {
			Files.createDirectories(target.getParent());
			Files.copy(resolve(storedName), target);
		} catch (IOException e) {
			throw new UncheckedIOException("첨부파일 복사 실패", e);
		}
		return copied;
	}

	private static String newName() {
		YearMonth ym = YearMonth.now();
		return "%d/%02d/%s".formatted(ym.getYear(), ym.getMonthValue(), UUID.randomUUID());
	}

	public Resource load(String storedName) {
		return new FileSystemResource(resolve(storedName));
	}

	public void delete(String storedName) {
		try {
			Files.deleteIfExists(resolve(storedName));
		} catch (IOException e) {
			throw new UncheckedIOException("첨부파일 삭제 실패", e);
		}
	}

	private Path resolve(String storedName) {
		Path path = root.resolve(storedName).normalize();
		if (!path.startsWith(root)) {
			throw new IllegalArgumentException("잘못된 파일 경로: " + storedName);
		}
		return path;
	}
}
