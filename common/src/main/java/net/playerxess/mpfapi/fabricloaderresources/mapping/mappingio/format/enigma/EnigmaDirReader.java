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

package net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.enigma;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Set;

import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingFlag;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingUtil;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingVisitor;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.adapter.ForwardingMappingVisitor;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.MappingFormat;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.tree.MappingTree;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.tree.MemoryMappingTree;

/**
 * {@linkplain MappingFormat#ENIGMA_DIR Enigma directory} reader.
 *
 * <p>Crashes if a second visit pass is requested without
 * {@link MappingFlag#NEEDS_MULTIPLE_PASSES} having been passed beforehand.
 */
public final class EnigmaDirReader {
	private EnigmaDirReader() {
	}

	public static void read(Path dir, MappingVisitor visitor) throws IOException {
		read(dir, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor);
	}

	public static void read(Path dir, String sourceNs, String targetNs, MappingVisitor visitor) throws IOException {
		if (!Files.exists(dir)) throw new IOException("Directory does not exist: " + dir);
		if (!Files.isDirectory(dir)) throw new IOException("Not a directory: " + dir);

		Set<MappingFlag> flags = visitor.getFlags();
		MappingVisitor parentVisitor = null;

		if (flags.contains(MappingFlag.NEEDS_ELEMENT_UNIQUENESS) || flags.contains(MappingFlag.NEEDS_MULTIPLE_PASSES)) {
			parentVisitor = visitor;
			visitor = new MemoryMappingTree();
		}

		if (visitor.visitHeader()) {
			visitor.visitNamespaces(sourceNs, Collections.singletonList(targetNs));
		}

		MappingVisitor delegatingVisitor = new ForwardingMappingVisitor(visitor) {
			@Override
			public boolean visitHeader() throws IOException {
				return false; // Namespaces have already been visited above, and Enigma files don't have any metadata
			}

			@Override
			public boolean visitContent() throws IOException {
				if (!visitedContent) { // Don't call next's visitContent() more than once
					visitedContent = true;
					visitContent = super.visitContent();
				}

				return visitContent;
			}

			@Override
			public boolean visitEnd() throws IOException {
				return true; // Don't forward since we're not done yet, there are more files to come
			}

			private boolean visitedContent;
			private boolean visitContent;
		};

		Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (file.getFileName().toString().endsWith("." + MappingFormat.ENIGMA_FILE.fileExt)) {
					EnigmaFileReader.read(Files.newBufferedReader(file), sourceNs, targetNs, delegatingVisitor);
				}

				return FileVisitResult.CONTINUE;
			}
		});

		if (visitor.visitEnd() && parentVisitor == null) return;

		if (parentVisitor == null) {
			throw new IllegalStateException("repeated visitation requested without NEEDS_MULTIPLE_PASSES");
		}

		((MappingTree) visitor).accept(parentVisitor);
	}
}
