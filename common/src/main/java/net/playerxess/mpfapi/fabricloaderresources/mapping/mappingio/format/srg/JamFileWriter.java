/*
 * Copyright (c) 2023 FabricMC
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

package net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.srg;

import java.io.IOException;
import java.io.Writer;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappedElementKind;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingFlag;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingWriter;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.MappingFormat;

/**
 * {@linkplain MappingFormat#JAM_FILE JAM file} writer.
 */
public final class JamFileWriter implements MappingWriter {
	public JamFileWriter(Writer writer) {
		this.writer = writer;
	}

	@Override
	public void close() throws IOException {
		writer.close();
	}

	@Override
	public Set<MappingFlag> getFlags() {
		return flags;
	}

	@Override
	public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {
	}

	@Override
	public boolean visitClass(String srcName) throws IOException {
		classSrcName = srcName;
		classDstName = null;

		return true;
	}

	@Override
	public boolean visitField(String srcName, @Nullable String srcDesc) throws IOException {
		if (srcDesc == null) {
			return false;
		}

		memberSrcName = srcName;
		memberSrcDesc = srcDesc;
		memberDstName = null;

		return true;
	}

	@Override
	public boolean visitMethod(String srcName, @Nullable String srcDesc) throws IOException {
		memberSrcName = srcName;
		memberSrcDesc = srcDesc;
		memberDstName = null;

		return true;
	}

	@Override
	public boolean visitMethodArg(int argPosition, int lvIndex, @Nullable String srcName) throws IOException {
		argSrcPosition = argPosition;
		argDstName = null;

		return true;
	}

	@Override
	public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, int endOpIdx, @Nullable String srcName) throws IOException {
		return false; // not supported, skip
	}

	@Override
	public void visitDstName(MappedElementKind targetKind, int namespace, String name) {
		if (namespace != 0) return;

		switch (targetKind) {
		case CLASS:
			classDstName = name;
			break;
		case FIELD:
		case METHOD:
			memberDstName = name;
			break;
		case METHOD_ARG:
			argDstName = name;
			break;
		default:
			throw new IllegalStateException("unexpected invocation for "+targetKind);
		}
	}

	@Override
	public boolean visitElementContent(MappedElementKind targetKind) throws IOException {
		boolean isClass = targetKind == MappedElementKind.CLASS;
		boolean isMethod = false;
		boolean isArg = false;

		if (isClass) {
			if (!classOnlyPass || classDstName == null) {
				return true;
			}

			write("CL ");
		} else if (targetKind == MappedElementKind.FIELD
				|| (isMethod = targetKind == MappedElementKind.METHOD)
				|| (isArg = targetKind == MappedElementKind.METHOD_ARG)) {
			if (classOnlyPass || memberSrcDesc == null) {
				return false;
			} else if (!isArg && memberDstName == null) {
				return isMethod;
			}

			if (isMethod) {
				write("MD ");
			} else if (!isArg) {
				write("FD ");
			} else {
				if (argSrcPosition == -1 || argDstName == null) return false;
				write("MP ");
			}
		} else {
			throw new IllegalStateException("unexpected invocation for "+targetKind);
		}

		write(classSrcName);
		writeSpace();

		if (isClass) {
			write(classDstName);
		} else {
			write(memberSrcName);
			writeSpace();

			write(memberSrcDesc);
			writeSpace();

			if (!isArg) {
				write(memberDstName);
			} else {
				write(Integer.toString(argSrcPosition));
				writeSpace();
				write(argDstName);
			}
		}

		writeLn();

		return isClass || isMethod;
	}

	@Override
	public void visitComment(MappedElementKind targetKind, String comment) throws IOException {
		// not supported, skip
	}

	@Override
	public boolean visitEnd() throws IOException {
		if (classOnlyPass) {
			classOnlyPass = false;
			return false;
		}

		classOnlyPass = true;
		return MappingWriter.super.visitEnd();
	}

	private void write(String str) throws IOException {
		writer.write(str);
	}

	private void writeSpace() throws IOException {
		writer.write(' ');
	}

	private void writeLn() throws IOException {
		writer.write('\n');
	}

	private static final Set<MappingFlag> flags = EnumSet.of(
			MappingFlag.NEEDS_SRC_FIELD_DESC,
			MappingFlag.NEEDS_SRC_METHOD_DESC,
			MappingFlag.NEEDS_MULTIPLE_PASSES);

	private final Writer writer;
	private boolean classOnlyPass = true;
	private String classSrcName;
	private String memberSrcName;
	private String memberSrcDesc;
	private int argSrcPosition = -1;
	private String classDstName;
	private String memberDstName;
	private String argDstName;
}
