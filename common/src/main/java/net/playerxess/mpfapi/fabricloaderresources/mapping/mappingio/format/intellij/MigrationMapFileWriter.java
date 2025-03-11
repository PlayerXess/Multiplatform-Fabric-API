/*
 * Copyright (c) 2024 FabricMC
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

package net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.intellij;

import java.io.IOException;
import java.io.Writer;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jetbrains.annotations.Nullable;

import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappedElementKind;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingFlag;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingWriter;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.MappingFormat;

/**
 * {@linkplain MappingFormat#INTELLIJ_MIGRATION_MAP_FILE IntelliJ migration map} writer.
 */
public final class MigrationMapFileWriter implements MappingWriter {
	public MigrationMapFileWriter(Writer writer) {
		this.writer = writer;
	}

	@Override
	public void close() throws IOException {
		try {
			if (xmlWriter != null) {
				if (!wroteName) {
					xmlWriter.writeCharacters("\n\t");
					xmlWriter.writeEmptyElement("name");
					xmlWriter.writeAttribute("value", MigrationMapConstants.MISSING_NAME);
				}

				if (!wroteOrder) {
					xmlWriter.writeCharacters("\n\t");
					xmlWriter.writeEmptyElement("order");
					xmlWriter.writeAttribute("value", MigrationMapConstants.DEFAULT_ORDER);
				}

				xmlWriter.writeCharacters("\n");
				xmlWriter.writeEndDocument();
				xmlWriter.writeCharacters("\n");
				xmlWriter.close();
			}
		} catch (XMLStreamException e) {
			throw new IOException(e);
		} finally {
			writer.close();
		}
	}

	@Override
	public Set<MappingFlag> getFlags() {
		return flags;
	}

	@Override
	public boolean visitHeader() throws IOException {
		assert xmlWriter == null;

		try {
			xmlWriter = XMLOutputFactory.newInstance().createXMLStreamWriter(writer);

			xmlWriter.writeStartDocument("UTF-8", "1.0");
			xmlWriter.writeCharacters("\n");
			xmlWriter.writeStartElement("migrationMap");
		} catch (FactoryConfigurationError | XMLStreamException e) {
			throw new IOException(e);
		}

		return true;
	}

	@Override
	public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {
	}

	@Override
	public void visitMetadata(String key, @Nullable String value) throws IOException {
		try {
			switch (key) {
			case "name":
				if (value == null) return;
				wroteName = true;
				break;
			case MigrationMapConstants.ORDER_KEY:
				if (value == null) return;
				wroteOrder = true;
				key = "order";
				break;
			}

			xmlWriter.writeCharacters("\n\t");
			xmlWriter.writeEmptyElement(key);

			if (value != null) {
				xmlWriter.writeAttribute("value", value);
			}
		} catch (XMLStreamException e) {
			throw new IOException(e);
		}
	}

	@Override
	public boolean visitClass(String srcName) throws IOException {
		this.srcName = srcName;
		this.dstName = null;

		return true;
	}

	@Override
	public boolean visitField(String srcName, @Nullable String srcDesc) throws IOException {
		return false;
	}

	@Override
	public boolean visitMethod(String srcName, @Nullable String srcDesc) throws IOException {
		return false;
	}

	@Override
	public boolean visitMethodArg(int argPosition, int lvIndex, @Nullable String srcName) throws IOException {
		return false;
	}

	@Override
	public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, int endOpIdx, @Nullable String srcName) throws IOException {
		return false;
	}

	@Override
	public void visitDstName(MappedElementKind targetKind, int namespace, String name) {
		if (namespace != 0) return;

		dstName = name;
	}

	@Override
	public boolean visitElementContent(MappedElementKind targetKind) throws IOException {
		if (dstName == null) return false;

		try {
			xmlWriter.writeCharacters("\n\t");
			xmlWriter.writeEmptyElement("entry");
			xmlWriter.writeAttribute("oldName", srcName.replace('/', '.'));
			xmlWriter.writeAttribute("newName", dstName.replace('/', '.'));
			xmlWriter.writeAttribute("type", "class");
		} catch (XMLStreamException e) {
			throw new IOException(e);
		}

		return false;
	}

	@Override
	public void visitComment(MappedElementKind targetKind, String comment) throws IOException {
		// not supported, skip
	}

	private static final Set<MappingFlag> flags = EnumSet.of(MappingFlag.NEEDS_ELEMENT_UNIQUENESS);

	private final Writer writer;
	private XMLStreamWriter xmlWriter;
	private boolean wroteName;
	private boolean wroteOrder;
	private String srcName;
	private String dstName;
}
