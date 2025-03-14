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

package net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.srg;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappedElementKind;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingFlag;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingUtil;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingVisitor;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.ColumnFileReader;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.MappingFormat;

/**
 * {@linkplain MappingFormat#CSRG_FILE CSRG file},
 * {@linkplain MappingFormat#TSRG_FILE TSRG file} and
 * {@linkplain MappingFormat#TSRG_2_FILE TSRG v2 file} reader.
 *
 * <p>Crashes if a second visit pass is requested without
 * {@link MappingFlag#NEEDS_MULTIPLE_PASSES} having been passed beforehand.
 */
public final class TsrgFileReader {
	private TsrgFileReader() {
	}

	public static List<String> getNamespaces(Reader reader) throws IOException {
		return getNamespaces(new ColumnFileReader(reader, '\t', ' '));
	}

	private static List<String> getNamespaces(ColumnFileReader reader) throws IOException {
		if (reader.nextCol("tsrg2")) { // tsrg2 magic
			List<String> ret = new ArrayList<>();
			String ns;

			while ((ns = reader.nextCol()) != null) {
				ret.add(ns);
			}

			return ret;
		} else { // assume tsrg1
			return Arrays.asList(MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK);
		}
	}

	public static void read(Reader reader, MappingVisitor visitor) throws IOException {
		read(reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor);
	}

	public static void read(Reader reader, String sourceNs, String targetNs, MappingVisitor visitor) throws IOException {
		read(new ColumnFileReader(reader, '\t', ' '), sourceNs, targetNs, visitor);
	}

	private static void read(ColumnFileReader reader, String sourceNs, String targetNs, MappingVisitor visitor) throws IOException {
		MappingFormat format = reader.nextCol("tsrg2") ? MappingFormat.TSRG_2_FILE : MappingFormat.TSRG_FILE;
		String srcNamespace;
		List<String> dstNamespaces;
		boolean readerMarked = false;

		if (format == MappingFormat.TSRG_2_FILE) {
			srcNamespace = reader.nextCol();
			if (srcNamespace == null || srcNamespace.isEmpty()) throw new IOException("no source namespace in TSRG v2 header");

			dstNamespaces = new ArrayList<>();
			String dstNamespace;

			while (!reader.isAtEol()) {
				dstNamespace = reader.nextCol();
				if (dstNamespace == null || dstNamespace.isEmpty()) throw new IOException("empty destination namespace in TSRG v2 header");
				dstNamespaces.add(dstNamespace);
			}

			reader.nextLine(0);
		} else {
			if (sourceNs == null || sourceNs.isEmpty()) throw new IllegalArgumentException("provided source namespace must not be null or empty");
			srcNamespace = sourceNs;

			if (targetNs == null || targetNs.isEmpty()) throw new IllegalArgumentException("provided target namespace must not be null or empty");
			dstNamespaces = Collections.singletonList(targetNs);
		}

		if (visitor.getFlags().contains(MappingFlag.NEEDS_MULTIPLE_PASSES)) {
			reader.mark();
			readerMarked = true;
		}

		int dstNsCount = dstNamespaces.size();
		List<String> nameTmp = dstNamespaces.size() > 1 ? new ArrayList<>(dstNamespaces.size() - 1) : null;

		for (;;) {
			if (visitor.visitHeader()) {
				visitor.visitNamespaces(srcNamespace, dstNamespaces);
			}

			if (visitor.visitContent()) {
				String lastClass = null;
				boolean visitLastClass = false; // Only used for CSRG

				do {
					if (reader.hasExtraIndents()) continue;
					reader.mark();
					String line = reader.nextCols(false);

					if ((line == null || line.isEmpty()) && reader.isAtEof()) {
						reader.discardMark();
						continue;
					}

					reader.reset();
					reader.discardMark();
					String[] parts = line.split("((?<= )|(?= ))"); // Split on spaces, but keep them

					if (format != MappingFormat.TSRG_2_FILE && parts.length >= 4 && !parts[3].startsWith("#")) { // CSRG
						format = MappingFormat.CSRG_FILE;
						String clsName = parts[0];
						if (clsName.isEmpty()) throw new IOException("missing class-name-a in line "+reader.getLineNumber());

						if (!clsName.equals(lastClass)) {
							lastClass = clsName;
							visitLastClass = visitor.visitClass(clsName) && visitor.visitElementContent(MappedElementKind.CLASS);
						}

						if (!visitLastClass) continue;
						String dstName;

						if (parts.length >= 6 && !parts[5].startsWith("#")) { // method
							dstName = parts.length == 6 ? null : parts[6];

							if (dstName == null || dstName.isEmpty() || dstName.startsWith("#")) {
								throw new IOException("missing method-name-b in line "+reader.getLineNumber());
							}

							if (visitor.visitMethod(parts[2], parts[4])) {
								visitor.visitDstName(MappedElementKind.METHOD, 0, dstName);
								visitor.visitElementContent(MappedElementKind.METHOD);
							}

							continue;
						} else if (parts.length >= 4) { // field
							dstName = parts.length == 4 ? null : parts[4];

							if (dstName == null || dstName.isEmpty() || dstName.startsWith("#")) {
								throw new IOException("missing field-name-b in line "+reader.getLineNumber());
							}

							if (visitor.visitField(parts[2], null)) {
								visitor.visitDstName(MappedElementKind.FIELD, 0, dstName);
								visitor.visitElementContent(MappedElementKind.FIELD);
							}

							continue;
						}

						throw new IllegalStateException("invalid CSRG line: "+line);
					}

					String srcName = reader.nextCol();
					if (srcName == null || srcName.endsWith("/")) continue;
					if (srcName.isEmpty()) throw new IOException("missing class-name-a in line "+reader.getLineNumber());

					lastClass = srcName;
					visitLastClass = visitor.visitClass(srcName);

					if (visitLastClass) {
						visitLastClass = readClass(reader, format == MappingFormat.TSRG_2_FILE, dstNsCount, nameTmp, visitor);
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

	private static boolean readClass(ColumnFileReader reader, boolean isTsrg2, int dstNsCount, List<String> nameTmp, MappingVisitor visitor) throws IOException {
		readDstNames(reader, MappedElementKind.CLASS, 0, dstNsCount, visitor);
		if (!visitor.visitElementContent(MappedElementKind.CLASS)) return false;

		while (reader.nextLine(1)) {
			if (reader.hasExtraIndents()) continue;

			String srcName = reader.nextCol();
			if (srcName == null || srcName.isEmpty()) throw new IOException("missing member-name-a in line "+reader.getLineNumber());

			String arg = reader.nextCol();
			if (arg == null) throw new IOException("missing member-desc-a/member-name-b in line "+reader.getLineNumber());

			if (arg.startsWith("(")) { // method: <nameA> <descA> <names>...
				if (visitor.visitMethod(srcName, arg)) {
					readMethod(reader, dstNsCount, visitor);
				}
			} else if (!isTsrg2) { // tsrg1 field, never has a desc: <nameA> <names>...
				if (visitor.visitField(srcName, null)) {
					if (arg.isEmpty()) throw new IOException("missing field-name-b in line "+reader.getLineNumber());
					visitor.visitDstName(MappedElementKind.FIELD, 0, arg);
					readElement(reader, MappedElementKind.FIELD, 1, dstNsCount, visitor);
				}
			} else { // tsrg2 field, may have desc
				for (int i = 0; i < dstNsCount - 1; i++) {
					String name = reader.nextCol();
					if (name == null) throw new IOException("missing name columns in line "+reader.getLineNumber());
					if (name.isEmpty()) throw new IOException("missing field-name-b in line "+reader.getLineNumber());
					nameTmp.add(name);
				}

				String lastName = reader.nextCol();
				int offset;
				String desc;

				if (lastName == null) { // no desc, arg is first dst name, nameTmp starts with 2nd dst name: <nameA> <names>...
					offset = 1;
					desc = null;
				} else { // arg is desc, nameTmp starts with 1st dst name: <nameA> <descA> <names>...
					offset = 0;
					desc = arg;
					if (desc.isEmpty()) throw new IOException("empty field-desc-a in line "+reader.getLineNumber());
				}

				if (visitor.visitField(srcName, desc)) {
					// first name without desc
					if (lastName == null && !arg.isEmpty()) visitor.visitDstName(MappedElementKind.FIELD, 0, arg);

					// middle names
					for (int i = 0; i < dstNsCount - 1; i++) {
						String name = nameTmp.get(i);
						if (!name.isEmpty()) visitor.visitDstName(MappedElementKind.FIELD, i + offset, name);
					}

					// last name with desc
					if (lastName != null && !lastName.isEmpty()) visitor.visitDstName(MappedElementKind.FIELD, dstNsCount - 1, lastName);

					visitor.visitElementContent(MappedElementKind.FIELD);
				}

				if (nameTmp != null) nameTmp.clear();
			}
		}

		return true;
	}

	private static void readMethod(ColumnFileReader reader, int dstNsCount, MappingVisitor visitor) throws IOException {
		readDstNames(reader, MappedElementKind.METHOD, 0, dstNsCount, visitor);
		if (!visitor.visitElementContent(MappedElementKind.METHOD)) return;

		while (reader.nextLine(2)) {
			if (reader.hasExtraIndents()) continue;

			if (reader.nextCol("static")) {
				// method is static
			} else {
				int lvIndex = reader.nextIntCol();
				if (lvIndex < 0) throw new IOException("missing/invalid parameter-lv-index in line "+reader.getLineNumber());

				String srcName = reader.nextCol();
				if (srcName == null) throw new IOException("missing parameter-name-a column in line "+reader.getLineNumber());
				if (srcName.isEmpty()) srcName = null;

				if (visitor.visitMethodArg(-1, lvIndex, srcName)) {
					readElement(reader, MappedElementKind.METHOD_ARG, 0, dstNsCount, visitor);
				}
			}
		}
	}

	private static void readElement(ColumnFileReader reader, MappedElementKind kind, int dstNsOffset, int dstNsCount, MappingVisitor visitor) throws IOException {
		readDstNames(reader, kind, dstNsOffset, dstNsCount, visitor);
		visitor.visitElementContent(kind);
	}

	private static void readDstNames(ColumnFileReader reader, MappedElementKind subjectKind, int dstNsOffset, int dstNsCount, MappingVisitor visitor) throws IOException {
		for (int dstNs = dstNsOffset; dstNs < dstNsCount; dstNs++) {
			String name = reader.nextCol();

			if (name == null) throw new IOException("missing name columns in line "+reader.getLineNumber());
			if (name.isEmpty()) throw new IOException("missing destination name in line "+reader.getLineNumber());

			visitor.visitDstName(subjectKind, dstNs, name);
		}
	}
}
