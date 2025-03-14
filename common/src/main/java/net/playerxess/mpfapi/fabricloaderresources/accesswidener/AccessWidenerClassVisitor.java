/*
 * Copyright (c) 2020 FabricMC
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

package net.playerxess.mpfapi.fabricloaderresources.accesswidener;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Applies rules from an {@link AccessWidener} by transforming Java classes using an ASM {@link ClassVisitor}.
 */
public final class AccessWidenerClassVisitor extends ClassVisitor {
	private final AccessWidener accessWidener;
	private String className;
	private int classAccess;

	AccessWidenerClassVisitor(int api, ClassVisitor classVisitor, AccessWidener accessWidener) {
		super(api, classVisitor);
		this.accessWidener = accessWidener;
	}

	public static ClassVisitor createClassVisitor(int api, ClassVisitor visitor, AccessWidener accessWidener) {
		return new AccessWidenerClassVisitor(api, visitor, accessWidener);
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		className = name;
		classAccess = access;

		super.visit(
				version,
				accessWidener.getClassAccess(name).apply(access, name, classAccess),
				name,
				signature,
				superName,
				interfaces
		);
	}

	@Override
	public void visitPermittedSubclass(String permittedSubclass) {
		AccessWidener.Access access = accessWidener.getClassAccess(className);

		if (access == AccessWidener.ClassAccess.EXTENDABLE || access == AccessWidener.ClassAccess.ACCESSIBLE_EXTENDABLE) {
			return;
		}

		super.visitPermittedSubclass(permittedSubclass);
	}

	@Override
	public void visitInnerClass(String name, String outerName, String innerName, int access) {
		super.visitInnerClass(
				name,
				outerName,
				innerName,
				accessWidener.getClassAccess(name).apply(access, name, classAccess)
		);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		return super.visitField(
				accessWidener.getFieldAccess(new EntryTriple(className, name, descriptor)).apply(access, name, classAccess),
				name,
				descriptor,
				signature,
				value
		);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		return new AccessWidenerMethodVisitor(super.visitMethod(
				accessWidener.getMethodAccess(new EntryTriple(className, name, descriptor)).apply(access, name, classAccess),
				name,
				descriptor,
				signature,
				exceptions
		));
	}

	private class AccessWidenerMethodVisitor extends MethodVisitor {
		AccessWidenerMethodVisitor(MethodVisitor methodVisitor) {
			super(AccessWidenerClassVisitor.this.api, methodVisitor);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
			if (opcode == Opcodes.INVOKESPECIAL && isTargetMethod(owner, name, descriptor)) {
				opcode = Opcodes.INVOKEVIRTUAL;
			}

			super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
			for (int i = 0; i < bootstrapMethodArguments.length; i++) {
				if (bootstrapMethodArguments[i] instanceof Handle) {
					Handle handle = (Handle) bootstrapMethodArguments[i];

					if (handle.getTag() == Opcodes.H_INVOKESPECIAL && isTargetMethod(handle.getOwner(), handle.getName(), handle.getDesc())) {
						bootstrapMethodArguments[i] = new Handle(Opcodes.H_INVOKEVIRTUAL, handle.getOwner(), handle.getName(), handle.getDesc(), handle.isInterface());
					}
				}
			}

			super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
		}

		private boolean isTargetMethod(String owner, String name, String descriptor) {
			return owner.equals(className) && !name.equals("<init>") && accessWidener.getMethodAccess(new EntryTriple(owner, name, descriptor)) != AccessWidener.MethodAccess.DEFAULT;
		}
	}
}
