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

package net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.proguard;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;

import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappedElementKind;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingFlag;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingUtil;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingVisitor;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.ColumnFileReader;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.MappingFormat;

/**
 * {@linkplain MappingFormat#PROGUARD_FILE ProGuard file} reader.
 *
 * <p>Crashes if a second visit pass is requested without
 * {@link MappingFlag#NEEDS_MULTIPLE_PASSES} having been passed beforehand.
 */
public final class ProGuardFileReader {
	private ProGuardFileReader() {
	}

	public static void read(Reader reader, MappingVisitor visitor) throws IOException {
		read(reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor);
	}

	public static void read(Reader reader, String sourceNs, String targetNs, MappingVisitor visitor) throws IOException {
		read(new ColumnFileReader(reader, /* random illegal character */ ';', ' '), sourceNs, targetNs, visitor);
	}

	private static void read(ColumnFileReader reader, String sourceNs, String targetNs, MappingVisitor visitor) throws IOException {
		boolean readerMarked = false;

		if (visitor.getFlags().contains(MappingFlag.NEEDS_MULTIPLE_PASSES)) {
			reader.mark();
			readerMarked = true;
		}

		StringBuilder descSb = null;

		for (;;) {
			if (visitor.visitHeader()) {
				visitor.visitNamespaces(sourceNs, Collections.singletonList(targetNs));
			}

			if (visitor.visitContent()) {
				if (descSb == null) descSb = new StringBuilder();

				String line;
				boolean visitClass = false;

				do {
					if ((line = reader.nextCols(false)) == null) continue;

					line = line.trim();
					if (line.isEmpty() || line.startsWith("#")) continue;

					if (line.endsWith(":")) { // class: <deobf> -> <obf>:
						int pos = line.indexOf(" -> ");
						if (pos < 0) throw new IOException("invalid proguard line (invalid separator): "+line);
						if (pos == 0) throw new IOException("invalid proguard line (empty src class): "+line);
						if (pos + 4 + 1 >= line.length()) throw new IOException("invalid proguard line (empty dst class): "+line);

						String name = line.substring(0, pos).replace('.', '/');
						visitClass = visitor.visitClass(name);

						if (visitClass) {
							String mappedName = line.substring(pos + 4, line.length() - 1).replace('.', '/');
							visitor.visitDstName(MappedElementKind.CLASS, 0, mappedName);
							visitClass = visitor.visitElementContent(MappedElementKind.CLASS);
						}
					} else if (visitClass) { // method or field: <type> <deobf> -> <obf>
						String[] parts = line.split(" ");

						if (parts.length != 4) throw new IOException("invalid proguard line (extra columns): "+line);
						if (parts[0].isEmpty()) throw new IOException("invalid proguard line (empty type): "+line);
						if (parts[1].isEmpty()) throw new IOException("invalid proguard line (empty src member): "+line);
						if (!parts[2].equals("->")) throw new IOException("invalid proguard line (invalid separator): "+line);
						if (parts[3].isEmpty()) throw new IOException("invalid proguard line (empty dst member): "+line);

						if (parts[1].indexOf('(') < 0) { // field: <type> <deobf> -> <obf>
							String name = parts[1];
							String desc = pgTypeToAsm(parts[0], descSb);

							if (visitor.visitField(name, desc)) {
								String mappedName = parts[3];
								visitor.visitDstName(MappedElementKind.FIELD, 0, mappedName);
								visitor.visitElementContent(MappedElementKind.FIELD);
							}
						} else { // method: [<lineStart>:<lineEndIncl>:]<rtype> [<clazz>.]<deobf><arg-desc>[:<deobf-lineStart>[:<deobf-lineEnd>]] -> <obf>
							// lineStart, lineEndIncl, rtype
							String part0 = parts[0];
							int pos = part0.indexOf(':');

							String retType;

							if (pos == -1) { // no obf line numbers
								retType = part0;
							} else {
								int pos2 = part0.indexOf(':', pos + 1);
								assert pos2 != -1;

								retType = part0.substring(pos2 + 1);
							}

							// clazz, deobf, arg-desc, obf
							String part1 = parts[1];
							pos = part1.indexOf('(');
							int pos3 = part1.indexOf(')', pos + 1); // arg-desc, obf
							assert pos3 != -1;

							if (part1.lastIndexOf('.', pos - 1) < 0 && part1.length() == pos3 + 1) { // no inlined method
								String name = part1.substring(0, pos);
								String argDesc = part1.substring(pos, pos3 + 1);
								String desc = pgDescToAsm(argDesc, retType, descSb);

								if (visitor.visitMethod(name, desc)) {
									String mappedName = parts[3];
									visitor.visitDstName(MappedElementKind.METHOD, 0, mappedName);
									visitor.visitElementContent(MappedElementKind.METHOD);
								}
							}
						}
					}
				} while (reader.nextLine(0));
			}

			if (visitor.visitEnd()) break;

			if (!readerMarked) {
				throw new IllegalStateException("repeated visitation requested without NEEDS_MULTIPLE_PASSES");
			}

			int markIdx = reader.reset();
			assert markIdx == 1;
		}
	}

	private static String pgDescToAsm(String pgArgDesc, String pgRetType, StringBuilder tmp) {
		tmp.setLength(0);
		tmp.append('(');

		if (pgArgDesc.length() > 2) { // not just ()
			int startPos = 1;
			boolean abort = false;

			do {
				int endPos = pgArgDesc.indexOf(',', startPos);

				if (endPos < 0) {
					endPos = pgArgDesc.length() - 1;
					abort = true;
				}

				appendPgTypeToAsm(pgArgDesc.substring(startPos, endPos), tmp);
				startPos = endPos + 1;
			} while (!abort);
		}

		tmp.append(')');
		if (pgRetType != null) appendPgTypeToAsm(pgRetType, tmp);

		return tmp.toString();
	}

	private static String pgTypeToAsm(String type, StringBuilder tmp) {
		tmp.setLength(0);
		appendPgTypeToAsm(type, tmp);

		return tmp.toString();
	}

	private static void appendPgTypeToAsm(String type, StringBuilder out) {
		assert !type.isEmpty();

		int arrayStart = type.indexOf('[');

		if (arrayStart != -1) {
			assert type.substring(arrayStart).matches("(\\[])+");

			int arrayDimensions = (type.length() - arrayStart) / 2; // 2 chars each: []

			for (int i = 0; i < arrayDimensions; i++) {
				out.append('[');
			}

			type = type.substring(0, arrayStart);
		}

		switch (type) {
		case "void": out.append('V'); break;
		case "boolean": out.append('Z'); break;
		case "char": out.append('C'); break;
		case "byte": out.append('B'); break;
		case "short": out.append('S'); break;
		case "int": out.append('I'); break;
		case "float": out.append('F'); break;
		case "long": out.append('J'); break;
		case "double": out.append('D'); break;
		default:
			out.append('L');
			out.append(type.replace('.', '/'));
			out.append(';');
		}
	}
}
