/*
 * Copyright (c) 2021 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.jetbrains.annotations.Nullable;

import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.MappingFormat;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.enigma.EnigmaDirReader;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.enigma.EnigmaFileReader;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.intellij.MigrationMapFileReader;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.jobf.JobfFileReader;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.proguard.ProGuardFileReader;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.simple.RecafSimpleFileReader;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.srg.JamFileReader;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.srg.SrgFileReader;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.srg.TsrgFileReader;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.tiny.Tiny1FileReader;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.tiny.Tiny2FileReader;

public final class MappingReader {
	private MappingReader() {
	}

	@Nullable
	public static MappingFormat detectFormat(Path path) throws IOException {
		if (Files.isDirectory(path)) {
			String enigmaExt = "." + MappingFormat.ENIGMA_FILE.fileExt;
			AtomicReference<MappingFormat> ret = new AtomicReference<>();

			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.getFileName().toString().endsWith(enigmaExt)) {
						ret.set(MappingFormat.ENIGMA_DIR);
						return FileVisitResult.TERMINATE;
					}

					return FileVisitResult.CONTINUE;
				}
			});

			return ret.get();
		}

		try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
			String fileName = path.getFileName().toString();
			int dotIdx = fileName.lastIndexOf('.');
			String fileExt = dotIdx >= 0 ? fileName.substring(dotIdx + 1) : null;

			return detectFormat(reader, fileExt);
		}
	}

	@Nullable
	public static MappingFormat detectFormat(Reader reader) throws IOException {
		return detectFormat(reader, null);
	}

	private static MappingFormat detectFormat(Reader reader, @Nullable String fileExt) throws IOException {
		char[] buffer = new char[DETECT_HEADER_LEN];
		int pos = 0;
		int len;

		// Be careful not to close the reader, that's up to the caller.
		BufferedReader br = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);

		br.mark(DETECT_HEADER_LEN);

		while (pos < buffer.length
				&& (len = br.read(buffer, pos, buffer.length - pos)) >= 0) {
			pos += len;
		}

		br.reset();
		if (pos < 3) return null;

		switch (String.valueOf(buffer, 0, 3)) {
		case "v1\t":
			return MappingFormat.TINY_FILE;
		case "tin":
			return MappingFormat.TINY_2_FILE;
		case "tsr": // tsrg2 <nsA> <nsB> ..<nsN>
			return MappingFormat.TSRG_2_FILE;
		case "CLA":
			return MappingFormat.ENIGMA_FILE;
		case "PK:":
		case "CL:":
		case "FD:":
		case "MD:":
			return detectSrgOrXsrg(br, fileExt);
		case "CL ":
		case "FD ":
		case "MD ":
		case "MP ":
			return MappingFormat.JAM_FILE;
		}

		String headerStr = String.valueOf(buffer, 0, pos);

		if (headerStr.contains("<migrationMap>")) {
			return MappingFormat.INTELLIJ_MIGRATION_MAP_FILE;
		} else if ((headerStr.startsWith("p ")
				|| headerStr.startsWith("c ")
				|| headerStr.startsWith("f ")
				|| headerStr.startsWith("m "))
				&& headerStr.contains(" = ")) {
			return MappingFormat.JOBF_FILE;
		} else if (headerStr.contains(" -> ")) {
			return MappingFormat.PROGUARD_FILE;
		} else if (headerStr.contains("\n\t")) {
			return MappingFormat.TSRG_FILE;
		}

		if (fileExt != null) {
			if (fileExt.equals(MappingFormat.CSRG_FILE.fileExt)) return MappingFormat.CSRG_FILE;
		}

		// TODO: Recaf Simple

		return null; // format unknown, not easily detectable or corrupted
	}

	private static MappingFormat detectSrgOrXsrg(BufferedReader reader, @Nullable String fileExt) throws IOException {
		String line;

		while ((line = reader.readLine()) != null) {
			if (line.startsWith("FD:")) {
				String[] parts = line.split(" ");

				if (parts.length < 5
						|| isEmptyOrStartsWithHash(parts[3])
						|| isEmptyOrStartsWithHash(parts[4])) {
					return MappingFormat.SRG_FILE;
				}

				return MappingFormat.XSRG_FILE;
			}
		}

		return MappingFormat.XSRG_FILE.fileExt.equals(fileExt) ? MappingFormat.XSRG_FILE : MappingFormat.SRG_FILE;
	}

	private static boolean isEmptyOrStartsWithHash(String string) {
		return string.isEmpty() || string.startsWith("#");
	}

	public static List<String> getNamespaces(Path file) throws IOException {
		return getNamespaces(file, null);
	}

	public static List<String> getNamespaces(Path file, MappingFormat format) throws IOException {
		if (format == null) {
			format = detectFormat(file);
			if (format == null) throw new IOException("invalid/unsupported mapping format");
		}

		if (format.features().hasNamespaces()) {
			try (Reader reader = Files.newBufferedReader(file)) {
				return getNamespaces(reader, format);
			}
		} else {
			return Arrays.asList(MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK);
		}
	}

	public static List<String> getNamespaces(Reader reader) throws IOException {
		return getNamespaces(reader, null);
	}

	public static List<String> getNamespaces(Reader reader, MappingFormat format) throws IOException {
		if (format == null) {
			if (!reader.markSupported()) reader = new BufferedReader(reader);
			reader.mark(DETECT_HEADER_LEN);
			format = detectFormat(reader);
			reader.reset();
			if (format == null) throw new IOException("invalid/unsupported mapping format");
		}

		if (format.features().hasNamespaces()) {
			checkReaderCompatible(format);

			switch (format) {
			case TINY_FILE:
				return Tiny1FileReader.getNamespaces(reader);
			case TINY_2_FILE:
				return Tiny2FileReader.getNamespaces(reader);
			case TSRG_2_FILE:
				return TsrgFileReader.getNamespaces(reader);
			default:
				throw new IllegalStateException();
			}
		} else {
			return Arrays.asList(MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK);
		}
	}

	/**
	 * Tries to detect the format of the given path and read it.
	 *
	 * @param path The path to read from. Can be a file or a directory.
	 * @param visitor The receiving visitor.
	 * @throws IOException If the format can't be detected or reading fails.
	 */
	public static void read(Path path, MappingVisitor visitor) throws IOException {
		read(path, null, visitor);
	}

	/**
	 * Tries to read the given path using the passed format's reader.
	 *
	 * @param path The path to read from. Can be a file or a directory.
	 * @param format The format to use. Has to match the path's format.
	 * @param visitor The receiving visitor.
	 * @throws IOException If reading fails.
	 */
	public static void read(Path path, MappingFormat format, MappingVisitor visitor) throws IOException {
		if (format == null) {
			format = detectFormat(path);
			if (format == null) throw new IOException("invalid/unsupported mapping format");
		}

		if (format.hasSingleFile()) {
			try (Reader reader = Files.newBufferedReader(path)) {
				read(reader, format, visitor);
			}
		} else {
			switch (format) {
			case ENIGMA_DIR:
				EnigmaDirReader.read(path, visitor);
				break;
			default:
				throw new IllegalStateException();
			}
		}
	}

	/**
	 * Tries to detect the reader's content's format and read it.
	 *
	 * @param reader The reader to read from.
	 * @param visitor The receiving visitor.
	 * @throws IOException If the format can't be detected or reading fails.
	 */
	public static void read(Reader reader, MappingVisitor visitor) throws IOException {
		read(reader, null, visitor);
	}

	/**
	 * Tries to read the reader's content using the passed format's mapping reader.
	 *
	 * @param reader The reader to read from.
	 * @param format The format to use. Has to match the reader's content's format.
	 * @param visitor The receiving visitor.
	 * @throws IOException If reading fails.
	 */
	public static void read(Reader reader, MappingFormat format, MappingVisitor visitor) throws IOException {
		if (format == null) {
			if (!reader.markSupported()) reader = new BufferedReader(reader);
			reader.mark(DETECT_HEADER_LEN);
			format = detectFormat(reader);
			reader.reset();
			if (format == null) throw new IOException("invalid/unsupported mapping format");
		}

		checkReaderCompatible(format);

		switch (format) {
		case TINY_FILE:
			Tiny1FileReader.read(reader, visitor);
			break;
		case TINY_2_FILE:
			Tiny2FileReader.read(reader, visitor);
			break;
		case ENIGMA_FILE:
			EnigmaFileReader.read(reader, visitor);
			break;
		case SRG_FILE:
		case XSRG_FILE:
			SrgFileReader.read(reader, visitor);
			break;
		case JAM_FILE:
			JamFileReader.read(reader, visitor);
			break;
		case CSRG_FILE:
		case TSRG_FILE:
		case TSRG_2_FILE:
			TsrgFileReader.read(reader, visitor);
			break;
		case PROGUARD_FILE:
			ProGuardFileReader.read(reader, visitor);
			break;
		case INTELLIJ_MIGRATION_MAP_FILE:
			MigrationMapFileReader.read(reader, visitor);
			break;
		case RECAF_SIMPLE_FILE:
			RecafSimpleFileReader.read(reader, visitor);
			break;
		case JOBF_FILE:
			JobfFileReader.read(reader, visitor);
			break;
		default:
			throw new IllegalStateException();
		}
	}

	private static void checkReaderCompatible(MappingFormat format) throws IOException {
		if (!format.hasSingleFile()) {
			throw new IOException("can't read mapping format "+format.name+" using a Reader, use the Path based API");
		}
	}

	private static final int DETECT_HEADER_LEN = 4096;
}
