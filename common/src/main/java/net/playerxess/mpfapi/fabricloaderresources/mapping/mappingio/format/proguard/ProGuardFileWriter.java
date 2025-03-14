/*
 * Copyright (c) 2022 FabricMC
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

package net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.proguard;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappedElementKind;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingWriter;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.MappingFormat;

/**
 * {@linkplain MappingFormat#PROGUARD_FILE ProGuard file} writer.
 */
public final class ProGuardFileWriter implements MappingWriter {
	private final Writer writer;
	private final String dstNamespaceString;
	private int dstNamespace = -1;
	private String clsSrcName;
	private String memberSrcName;
	private String memberSrcDesc;
	private String dstName;
	private boolean classContentVisitPending;

	/**
	 * Constructs a ProGuard mapping writer that uses
	 * the first destination namespace (index 0).
	 *
	 * @param writer The writer where the mappings will be written.
	 */
	public ProGuardFileWriter(Writer writer) {
		this(writer, 0);
	}

	/**
	 * Constructs a ProGuard mapping writer.
	 *
	 * @param writer The writer where the mappings will be written.
	 * @param dstNamespace The namespace index to write as the destination namespace, must be at least 0.
	 */
	public ProGuardFileWriter(Writer writer, int dstNamespace) {
		this.writer = Objects.requireNonNull(writer, "writer cannot be null");
		this.dstNamespace = dstNamespace;
		this.dstNamespaceString = null;

		if (dstNamespace < 0) {
			throw new IllegalArgumentException("Namespace must be non-negative, found " + dstNamespace);
		}
	}

	/**
	 * Constructs a ProGuard mapping writer.
	 *
	 * @param writer The writer where the mappings will be written.
	 * @param dstNamespace The namespace name to write as the destination namespace.
	 */
	public ProGuardFileWriter(Writer writer, String dstNamespace) {
		this.writer = Objects.requireNonNull(writer, "writer cannot be null");
		this.dstNamespaceString = Objects.requireNonNull(dstNamespace, "namespace cannot be null");
	}

	/**
	 * Closes the internal {@link Writer}.
	 */
	@Override
	public void close() throws IOException {
		writer.close();
	}

	@Override
	public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {
		if (dstNamespaceString != null) {
			dstNamespace = dstNamespaces.indexOf(dstNamespaceString);

			if (dstNamespace == -1) {
				throw new RuntimeException("Invalid destination namespace '" + dstNamespaceString + "' not in [" + String.join(", ", dstNamespaces) + ']');
			}
		}

		if (dstNamespace >= dstNamespaces.size()) {
			throw new IndexOutOfBoundsException("Namespace " + dstNamespace + " doesn't exist in [" + String.join(", ", dstNamespaces) + ']');
		}
	}

	@Override
	public boolean visitClass(String srcName) throws IOException {
		clsSrcName = srcName;

		return true;
	}

	@Override
	public boolean visitField(String srcName, @Nullable String srcDesc) throws IOException {
		if (srcDesc == null) {
			return false;
		}

		memberSrcName = srcName;
		memberSrcDesc = srcDesc;

		return true;
	}

	@Override
	public boolean visitMethod(String srcName, @Nullable String srcDesc) throws IOException {
		if (srcDesc == null) {
			return false;
		}

		memberSrcName = srcName;
		memberSrcDesc = srcDesc;

		return true;
	}

	@Override
	public boolean visitMethodArg(int argPosition, int lvIndex, @Nullable String srcName) throws IOException {
		return false; // not supported, skip
	}

	@Override
	public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, int endOpIdx, @Nullable String srcName) throws IOException {
		return false; // not supported, skip
	}

	@Override
	public void visitDstName(MappedElementKind targetKind, int namespace, String name) throws IOException {
		if (this.dstNamespace != namespace) {
			return;
		}

		dstName = name;
	}

	@Override
	public boolean visitElementContent(MappedElementKind targetKind) throws IOException {
		if (targetKind == MappedElementKind.CLASS) {
			if (dstName == null) {
				classContentVisitPending = true;
				return true;
			}
		} else {
			if (dstName == null) {
				return false;
			} else if (classContentVisitPending) {
				String memberDstName = dstName;
				dstName = clsSrcName;
				visitElementContent(MappedElementKind.CLASS);
				classContentVisitPending = false;
				dstName = memberDstName;
			}
		}

		switch (targetKind) {
		case CLASS:
			writer.write(toJavaClassName(clsSrcName));
			dstName = toJavaClassName(dstName) + ":";
			break;
		case FIELD:
			writeIndent();
			writer.write(toJavaType(memberSrcDesc));
			writer.write(' ');
			writer.write(memberSrcName);
			break;
		case METHOD:
			writeIndent();
			writer.write(toJavaType(memberSrcDesc.substring(memberSrcDesc.indexOf(')', 1) + 1)));
			writer.write(' ');
			writer.write(memberSrcName);
			writer.write('(');
			List<String> argTypes = extractArgumentTypes(memberSrcDesc);

			for (int i = 0; i < argTypes.size(); i++) {
				if (i > 0) {
					writer.write(',');
				}

				writer.write(argTypes.get(i));
			}

			writer.write(')');
			break;
		default:
			throw new IllegalStateException("unexpected invocation for "+targetKind);
		}

		writeArrow();
		writer.write(dstName);
		writer.write('\n');

		dstName = null;
		return targetKind == MappedElementKind.CLASS;
	}

	@Override
	public void visitComment(MappedElementKind targetKind, String comment) throws IOException {
		// not supported, skip
	}

	private void writeArrow() throws IOException {
		writer.write(" -> ");
	}

	private void writeIndent() throws IOException {
		// This has to be exactly 4 spaces.
		writer.write("    ");
	}

	/**
	 * Replaces the slashes as package separators with dots
	 * since ProGuard uses Java-like dotted class names.
	 */
	private static String toJavaClassName(String name) {
		return name.replace('/', '.');
	}

	private static String toJavaType(String descriptor) {
		StringBuilder result = new StringBuilder();
		int arrayLevel = 0;

		for (int i = 0; i < descriptor.length(); i++) {
			switch (descriptor.charAt(i)) {
			case '[': arrayLevel++; break;
			case 'B': result.append("byte"); break;
			case 'S': result.append("short"); break;
			case 'I': result.append("int"); break;
			case 'J': result.append("long"); break;
			case 'F': result.append("float"); break;
			case 'D': result.append("double"); break;
			case 'C': result.append("char"); break;
			case 'Z': result.append("boolean"); break;
			case 'V': result.append("void"); break;
			case 'L':
				while (i + 1 < descriptor.length()) {
					char c = descriptor.charAt(++i);

					if (c == '/') {
						result.append('.');
					} else if (c == ';') {
						break;
					} else {
						result.append(c);
					}
				}

				break;
			default: throw new IllegalArgumentException("Unknown character in descriptor: " + descriptor.charAt(i));
			}
		}

		// TODO: This can be replaced by String.repeat in modern Java
		while (arrayLevel > 0) {
			result.append("[]");
			arrayLevel--;
		}

		return result.toString();
	}

	private List<String> extractArgumentTypes(String desc) {
		List<String> argTypes = new ArrayList<>();
		int index = 1; // First char is always '('

		while (desc.charAt(index) != ')') {
			int start = index;

			while (desc.charAt(index) == '[') {
				index++;
			}

			if (desc.charAt(index) == 'L') {
				index = desc.indexOf(';', index) + 1;
			} else {
				index++;
			}

			argTypes.add(toJavaType(desc.substring(start, index)));
		}

		return argTypes;
	}
}
