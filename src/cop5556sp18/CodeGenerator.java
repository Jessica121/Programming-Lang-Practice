/**
 * Starter code for CodeGenerator.java used n the class project in COP5556 Programming Language Principles 
 * at the University of Florida, Spring 2018.
 * 
 * This software is solely for the educational benefit of students 
 * enrolled in the course during the Spring 2018 semester.  
 * 
 * This software, and any software derived from it,  may not be shared with others or posted to public web sites,
 * either during the course or afterwards.
 * 
 *  @Beverly A. Sanders, 2018
 */


package cop5556sp18;
import java.util.*;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;


import cop5556sp18.Types.Type;
import cop5556sp18.AST.ASTNode;
import cop5556sp18.AST.ASTVisitor;
import cop5556sp18.AST.Block;
import cop5556sp18.AST.Declaration;
import cop5556sp18.AST.ExpressionBinary;
import cop5556sp18.AST.ExpressionBooleanLiteral;
import cop5556sp18.AST.ExpressionConditional;
import cop5556sp18.AST.ExpressionFloatLiteral;
import cop5556sp18.AST.ExpressionFunctionAppWithExpressionArg;
import cop5556sp18.AST.ExpressionFunctionAppWithPixel;
import cop5556sp18.AST.ExpressionIdent;
import cop5556sp18.AST.ExpressionIntegerLiteral;
import cop5556sp18.AST.ExpressionPixel;
import cop5556sp18.AST.ExpressionPixelConstructor;
import cop5556sp18.AST.ExpressionPredefinedName;
import cop5556sp18.AST.ExpressionUnary;
import cop5556sp18.AST.LHSIdent;
import cop5556sp18.AST.LHSPixel;
import cop5556sp18.AST.LHSSample;
import cop5556sp18.AST.PixelSelector;
import cop5556sp18.AST.Program;
import cop5556sp18.AST.StatementAssign;
import cop5556sp18.AST.StatementIf;
import cop5556sp18.AST.StatementInput;
import cop5556sp18.AST.StatementShow;
import cop5556sp18.AST.StatementSleep;
import cop5556sp18.AST.StatementWhile;
import cop5556sp18.AST.StatementWrite;

import cop5556sp18.CodeGenUtils;
import cop5556sp18.Scanner.Kind;

public class CodeGenerator implements ASTVisitor, Opcodes {

	/**
	 * All methods and variable static.
	 */
	FieldVisitor fv;
	static String fieldName;
	static String fieldType;
	static Object initValue;
	int slot;

	static final int Z = 255;
	List<Declaration> allDec = new ArrayList<>();
	ClassWriter cw;
	String className;
	String classDesc;
	String sourceFileName;

	MethodVisitor mv; // visitor of method currently under construction

	/** Indicates whether genPrint and genPrintTOS should generate code. */
	final boolean DEVEL;
	final boolean GRADE;

	final int defaultWidth;
	final int defaultHeight;
	// final boolean if = false;
	/**
	 * @param DEVEL
	 *            used as parameter to genPrint and genPrintTOS
	 * @param GRADE
	 *            used as parameter to genPrint and genPrintTOS
	 * @param sourceFileName
	 *            name of source file, may be null.
	 * @param defaultWidth
	 *            default width of images
	 * @param defaultHeight
	 *            default height of images
	 */
	public CodeGenerator(boolean DEVEL, boolean GRADE, String sourceFileName,
			int defaultWidth, int defaultHeight) {
		super();
		this.DEVEL = DEVEL;
		this.GRADE = GRADE;
		this.sourceFileName = sourceFileName;
		this.defaultWidth = defaultWidth;
		this.defaultHeight = defaultHeight;
		this.slot = 1;
	}

	@Override
	public Object visitBlock(Block block, Object arg) throws Exception {
			Label startBlock = new Label();
			Label endBlock = new Label();
			mv.visitLabel(startBlock);
			for (ASTNode node : block.decsOrStatements) {
				if(node.getClass() == Declaration.class) {
					Declaration dec = (Declaration)node;
					if(dec.getStartLevel() == null) {
						dec.setStartLevel(startBlock);	
					}
					if(dec.getEndLevel() == null) {
						dec.setEndLevel(endBlock);	
					}
				}
				node.visit(this, null);
			}
			mv.visitLabel(endBlock);
			return null;
	}

	@Override
	public Object visitBooleanLiteral(
			ExpressionBooleanLiteral expressionBooleanLiteral, Object arg)
			throws Exception {
		mv.visitLdcInsn(expressionBooleanLiteral.value);
		return null;
	}

	@Override
	public Object visitDeclaration(Declaration declaration, Object arg)
			throws Exception {
		
		declaration.setSlotNum(slot); 
		slot = slot + 1;
				if(declaration.typeName == Type.IMAGE) {
					if(declaration.width != null && declaration.height != null) {
					/* visit the Expressions to generate code to evaluate them and leave their value on the stack. 
					 * Then generate code to instantiate an image (invoke RuntimeImageSupport.makeImage)*/
						declaration.width.visit(this, arg);
						declaration.height.visit(this, arg);
					} else if(declaration.width == null && declaration.height == null) {
						mv.visitLdcInsn(defaultWidth); // put on top of stack
						mv.visitLdcInsn(defaultHeight);
					}
					mv.visitMethodInsn(INVOKESTATIC, "cop5556sp18/RuntimeImageSupport", "makeImage", RuntimeImageSupport.makeImageSig, false);
					mv.visitVarInsn(ASTORE, declaration.getSlotNum());
				} else{
					mv.visitInsn(ACONST_NULL);
					mv.visitVarInsn(ASTORE, declaration.getSlotNum());
				}
		
		return null;
	}

	@Override
	public Object visitExpressionBinary(ExpressionBinary expressionBinary, Object arg) throws Exception {
		Label labelStart = new Label(), labelEnd = new Label();

		if (expressionBinary.leftExpression != null) expressionBinary.leftExpression.visit(this, arg);
		if (expressionBinary.rightExpression != null) expressionBinary.rightExpression.visit(this, arg);
		// left, right || t1, t2
		Type t1 = expressionBinary.leftExpression.typeName, t2 = expressionBinary.rightExpression.typeName;
		if (expressionBinary.op == Kind.OP_PLUS) {
			if(t1 == Type.INTEGER && t2 == Type.INTEGER) mv.visitInsn(IADD);
			else if(t1 == Type.FLOAT && t2 == Type.INTEGER) {
				mv.visitInsn(I2F);
				mv.visitInsn(FADD);
			} else if(t2 == Type.FLOAT && t1 == Type.INTEGER) {
				mv.visitInsn(Opcodes.SWAP);
				mv.visitInsn(I2F);
				mv.visitInsn(Opcodes.SWAP); // swap them back
				mv.visitInsn(FADD);
			} else if(t2 == Type.FLOAT && t1 == Type.FLOAT) {
				mv.visitInsn(FADD);
			}
		} else if (expressionBinary.op == Kind.OP_MINUS) {
			if(t1 == Type.INTEGER && t2 == Type.INTEGER) mv.visitInsn(ISUB);
			else if(t1 == Type.FLOAT && t2 == Type.INTEGER) {
				mv.visitInsn(I2F);
				mv.visitInsn(FSUB);
			} else if(t2 == Type.FLOAT && t1 == Type.INTEGER) {
				mv.visitInsn(Opcodes.SWAP);
				mv.visitInsn(I2F);
				mv.visitInsn(Opcodes.SWAP); // swap them back
				mv.visitInsn(FSUB);
			} else if(t2 == Type.FLOAT && t1 == Type.FLOAT) {
				mv.visitInsn(FSUB);
			}
		}  else if (expressionBinary.op == Kind.OP_TIMES) {
			if(t1 == Type.INTEGER && t2 == Type.INTEGER) mv.visitInsn(IMUL);
			else if(t1 == Type.FLOAT && t2 == Type.INTEGER) {
				mv.visitInsn(I2F);
				mv.visitInsn(FMUL);
			} else if(t2 == Type.FLOAT && t1 == Type.INTEGER) {
				mv.visitInsn(Opcodes.SWAP);
				mv.visitInsn(I2F);
				mv.visitInsn(Opcodes.SWAP); // swap them back
				mv.visitInsn(FMUL);
			} else if(t2 == Type.FLOAT && t1 == Type.FLOAT) {
				mv.visitInsn(FMUL);
			}
		} else if (expressionBinary.op == Kind.OP_DIV) {
			if(t1 == Type.INTEGER && t2 == Type.INTEGER) mv.visitInsn(IDIV);
			else if(t1 == Type.FLOAT && t2 == Type.INTEGER) {
				mv.visitInsn(I2F);
				mv.visitInsn(FDIV);
			} else if(t2 == Type.FLOAT && t1 == Type.INTEGER) {
				mv.visitInsn(Opcodes.SWAP);
				mv.visitInsn(I2F);
				mv.visitInsn(Opcodes.SWAP); // swap them back
				mv.visitInsn(FDIV);
			} else if(t2 == Type.FLOAT && t1 == Type.FLOAT) {
				mv.visitInsn(FDIV);
			}
		} else if (expressionBinary.op == Kind.OP_MOD) { 
			if(t1 == Type.INTEGER && t2 == Type.INTEGER) mv.visitInsn(IREM);
			else if(t1 == Type.FLOAT && t2 == Type.INTEGER) {
				mv.visitInsn(I2F);
				mv.visitInsn(FREM);
			} else if(t2 == Type.FLOAT && t1 == Type.INTEGER) {
				mv.visitInsn(Opcodes.SWAP);
				mv.visitInsn(I2F);
				mv.visitInsn(Opcodes.SWAP); // swap them back
				mv.visitInsn(FREM);
			} else if(t2 == Type.FLOAT && t1 == Type.FLOAT) {
				mv.visitInsn(FREM);
			}
		} else if (expressionBinary.op == Kind.OP_AND) { 
			mv.visitInsn(IAND);	
		} else if (expressionBinary.op == Kind.OP_OR) {
			mv.visitInsn(IOR);
		}  else if (expressionBinary.op == Kind.OP_EQ) {
			mv.visitJumpInsn(IF_ICMPEQ, labelStart);
			mv.visitLdcInsn(0);
			mv.visitJumpInsn(GOTO, labelEnd);
			mv.visitLabel(labelStart);
			mv.visitLdcInsn(1);
			mv.visitLabel(labelEnd);
		}else if (expressionBinary.op == Kind.OP_LT) {
			mv.visitJumpInsn(IF_ICMPLT, labelStart);
			mv.visitLdcInsn(0);
			mv.visitJumpInsn(GOTO, labelEnd);
			mv.visitLabel(labelStart);
			mv.visitLdcInsn(1);
			mv.visitLabel(labelEnd);
		} else if (expressionBinary.op == Kind.OP_NEQ) {
			mv.visitJumpInsn(IF_ICMPNE, labelStart);
			mv.visitLdcInsn(0);
			mv.visitJumpInsn(GOTO, labelEnd);
			mv.visitLabel(labelStart);
			mv.visitLdcInsn(1);
			mv.visitLabel(labelEnd);
		} else if (expressionBinary.op == Kind.OP_GE) {
			mv.visitJumpInsn(IF_ICMPGE, labelStart);
			mv.visitLdcInsn(0);
			mv.visitJumpInsn(GOTO, labelEnd);
			mv.visitLabel(labelStart);
			mv.visitLdcInsn(1);
			mv.visitLabel(labelEnd);
		} else if (expressionBinary.op == Kind.OP_LE) {
			mv.visitJumpInsn(IF_ICMPLE, labelStart);
			mv.visitLdcInsn(0);
			mv.visitJumpInsn(GOTO, labelEnd);
			mv.visitLabel(labelStart);
			mv.visitLdcInsn(1);
			mv.visitLabel(labelEnd);
		} else if (expressionBinary.op == Kind.OP_GT ) {
			mv.visitJumpInsn(IF_ICMPGT, labelStart);
			mv.visitLdcInsn(0);
			mv.visitJumpInsn(GOTO, labelEnd);
			mv.visitLabel(labelStart);
			mv.visitLdcInsn(1);
			mv.visitLabel(labelEnd);	
		} else if (expressionBinary.op == Kind.OP_LT) {
			mv.visitJumpInsn(IF_ICMPLT, labelStart);
			mv.visitLdcInsn(0);
			mv.visitJumpInsn(GOTO, labelEnd);
			mv.visitLabel(labelStart);
			mv.visitLdcInsn(1);
			mv.visitLabel(labelEnd);	
		} else if (expressionBinary.op == Kind.OP_POWER) {
			/*Implement POWER by converting both arguments to double, invoking java/lang/Math.pow, 
			 * and converting the result back to the appropriate type. 
			 */
			if(t1 == Type.INTEGER && t2 == Type.INTEGER) 
				mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "pow", RuntimeFunc.powSig, false);
			else if(t1 == Type.FLOAT && t2 == Type.INTEGER) {
				mv.visitInsn(I2F);
				mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "pow2", RuntimeFunc.powSig2, false);
			} else if(t2 == Type.FLOAT && t1 == Type.INTEGER) {
				mv.visitInsn(Opcodes.SWAP);
				mv.visitInsn(I2F);
				mv.visitInsn(Opcodes.SWAP); // swap them back
				mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "pow2", RuntimeFunc.powSig2, false);
			} else if(t2 == Type.FLOAT && t1 == Type.FLOAT) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "pow2", RuntimeFunc.powSig2, false);
			}
		}
		return null;
	}

	@Override
	public Object visitExpressionConditional(
			ExpressionConditional expressionConditional, Object arg)
			throws Exception {
		Label tru = new Label(), fal = new Label();
		if(expressionConditional.guard != null) expressionConditional.guard.visit(this, arg);
		mv.visitJumpInsn(IFEQ, fal);
		expressionConditional.trueExpression.visit(this, arg);
		mv.visitJumpInsn(GOTO, tru);
		mv.visitLabel(fal);
		expressionConditional.falseExpression.visit(this, arg);
		mv.visitLabel(tru);
		return null;
	}

	@Override
	public Object visitExpressionFloatLiteral(
			ExpressionFloatLiteral expressionFloatLiteral, Object arg)
			throws Exception {
		mv.visitLdcInsn(expressionFloatLiteral.value);
		return null;
	}

	@Override
	public Object visitExpressionFunctionAppWithExpressionArg(
			ExpressionFunctionAppWithExpressionArg expressionFunctionAppWithExpressionArg,
			Object arg) throws Exception {
		expressionFunctionAppWithExpressionArg.e.visit(this, arg);

		if (expressionFunctionAppWithExpressionArg.function == Kind.KW_log) {
			if(expressionFunctionAppWithExpressionArg.e.getType() == Type.INTEGER) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "log", RuntimeFunc.logSig, false);
			}
			else if(expressionFunctionAppWithExpressionArg.e.getType() == Type.FLOAT) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "log2", RuntimeFunc.logSig2, false);
			}
		} else if(expressionFunctionAppWithExpressionArg.function == Kind.KW_abs) {
			if(expressionFunctionAppWithExpressionArg.e.getType() == Type.INTEGER) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "abs", RuntimeFunc.absSig, false);
			}
			else if(expressionFunctionAppWithExpressionArg.e.getType() == Type.FLOAT) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "abs2", RuntimeFunc.absSig2, false);
			}
		} else if(expressionFunctionAppWithExpressionArg.function == Kind.KW_sin) {
			if(expressionFunctionAppWithExpressionArg.e.getType() == Type.INTEGER) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "sin", RuntimeFunc.sinSig, false);
			}
			else if(expressionFunctionAppWithExpressionArg.e.getType() == Type.FLOAT) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "sin2", RuntimeFunc.sinSig2, false);
			}
		} else if(expressionFunctionAppWithExpressionArg.function == Kind.KW_cos) {
			if(expressionFunctionAppWithExpressionArg.e.getType() == Type.INTEGER) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "cos", RuntimeFunc.cosSig, false);
			}
			else if(expressionFunctionAppWithExpressionArg.e.getType() == Type.FLOAT) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "cos2", RuntimeFunc.cosSig2, false);
			}
		} else if(expressionFunctionAppWithExpressionArg.function == Kind.KW_atan) {
			if(expressionFunctionAppWithExpressionArg.e.getType() == Type.INTEGER) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "atan", RuntimeFunc.atanSig, false);
			}
			else if(expressionFunctionAppWithExpressionArg.e.getType() == Type.FLOAT) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "atan2", RuntimeFunc.atanSig2, false);
			}
		} else if(expressionFunctionAppWithExpressionArg.function == Kind.KW_red) {
			if(expressionFunctionAppWithExpressionArg.e.getType() == Type.INTEGER) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimePixelOps.className, "getRed", RuntimePixelOps.getRedSig, false);
			}
			else if(expressionFunctionAppWithExpressionArg.e.getType() == Type.FLOAT) {
				mv.visitInsn(F2I);
				mv.visitMethodInsn(INVOKESTATIC, RuntimePixelOps.className, "getRed", RuntimePixelOps.getRedSig, false);
			}
		} else if(expressionFunctionAppWithExpressionArg.function == Kind.KW_green) {
			if(expressionFunctionAppWithExpressionArg.e.getType() == Type.INTEGER) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimePixelOps.className, "getGreen", RuntimePixelOps.getGreenSig, false);
			}
			else if(expressionFunctionAppWithExpressionArg.e.getType() == Type.FLOAT) {
				mv.visitInsn(F2I);
				mv.visitMethodInsn(INVOKESTATIC, RuntimePixelOps.className, "getGreen", RuntimePixelOps.getGreenSig, false);
			}
		} else if(expressionFunctionAppWithExpressionArg.function == Kind.KW_blue) {
			if(expressionFunctionAppWithExpressionArg.e.getType() == Type.INTEGER) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimePixelOps.className, "getBlue", RuntimePixelOps.getBlueSig, false);
			}
			else if(expressionFunctionAppWithExpressionArg.e.getType() == Type.FLOAT) {
				mv.visitInsn(F2I);
				mv.visitMethodInsn(INVOKESTATIC, RuntimePixelOps.className, "getBlue", RuntimePixelOps.getBlueSig, false);
			}
		} else if(expressionFunctionAppWithExpressionArg.function == Kind.KW_alpha) {
			if(expressionFunctionAppWithExpressionArg.e.getType() == Type.INTEGER) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimePixelOps.className, "getAlpha", RuntimePixelOps.getAlphaSig, false);
			}
			else if(expressionFunctionAppWithExpressionArg.e.getType() == Type.FLOAT) {
				mv.visitInsn(F2I);
				mv.visitMethodInsn(INVOKESTATIC, RuntimePixelOps.className, "getAlpha", RuntimePixelOps.getAlphaSig, false);
			}
		} else if(expressionFunctionAppWithExpressionArg.function == Kind.KW_width) {
			if(expressionFunctionAppWithExpressionArg.e.getType() == Type.IMAGE) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimeImageSupport.className, "getWidth", RuntimeImageSupport.getWidthSig, false);
			}
		} else if(expressionFunctionAppWithExpressionArg.function == Kind.KW_height) {
			if(expressionFunctionAppWithExpressionArg.e.getType() == Type.IMAGE) {
				mv.visitMethodInsn(INVOKESTATIC, RuntimeImageSupport.className, "getHeight", RuntimeImageSupport.getHeightSig, false);
			}
		} else if(expressionFunctionAppWithExpressionArg.function == Kind.KW_float) {
			if(expressionFunctionAppWithExpressionArg.e.getType() == Type.INTEGER) {
			    mv.visitInsn(I2F);
			} 
		} else if(expressionFunctionAppWithExpressionArg.function == Kind.KW_int) {
			if(expressionFunctionAppWithExpressionArg.e.getType() == Type.FLOAT) {
				mv.visitInsn(F2I);
			}
		} 

		return null;
	}

	@Override
	public Object visitExpressionFunctionAppWithPixel(
			ExpressionFunctionAppWithPixel expressionFunctionAppWithPixel,
			Object arg) throws Exception {
		expressionFunctionAppWithPixel.e0.visit(this, arg);
		expressionFunctionAppWithPixel.e1.visit(this, arg);
		
		if(expressionFunctionAppWithPixel.name == Kind.KW_polar_a) {
			mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "polar_a", RuntimeFunc.aSig, false);
		} else if(expressionFunctionAppWithPixel.name == Kind.KW_cart_x) {
			mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "cart_x", RuntimeFunc.xSig, false);
		} else if(expressionFunctionAppWithPixel.name == Kind.KW_cart_y) {
			mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "cart_y", RuntimeFunc.ySig, false);
		} else if(expressionFunctionAppWithPixel.name == Kind.KW_polar_r) {
			mv.visitMethodInsn(INVOKESTATIC, RuntimeFunc.className, "polar_r", RuntimeFunc.rSig, false);
		}
		
		return null;
	}

	@Override
	public Object visitExpressionIdent(ExpressionIdent expressionIdent,
			Object arg) throws Exception {
		Declaration dec = expressionIdent.dec;
			if (dec.getTypeName() == Type.INTEGER || dec.getTypeName() == Type.BOOLEAN) {
				mv.visitVarInsn(ILOAD, dec.getSlotNum());
			} else if (dec.getTypeName() == Type.FLOAT) {
				mv.visitVarInsn(FLOAD, dec.getSlotNum());
			} else if (dec.getTypeName() == Type.FILE) {
				mv.visitVarInsn(ALOAD, dec.getSlotNum());
			} else if (dec.getTypeName() == Type.IMAGE) mv.visitVarInsn(ALOAD, dec.getSlotNum());

		return null;
	}

	@Override
	public Object visitExpressionIntegerLiteral(
			ExpressionIntegerLiteral expressionIntegerLiteral, Object arg)
			throws Exception {
		mv.visitLdcInsn(expressionIntegerLiteral.value);
		return null;
	}

	@Override
	public Object visitExpressionPixel(ExpressionPixel expressionPixel,
			Object arg) throws Exception {
		mv.visitVarInsn(ALOAD, expressionPixel.dec.getSlotNum());
		if(expressionPixel.pixelSelector != null) {
			expressionPixel.pixelSelector.visit(this, arg);
			mv.visitMethodInsn(INVOKESTATIC, RuntimeImageSupport.className, "getPixel", RuntimeImageSupport.getPixelSig,false);
		}
		return null;
	}

	@Override
	public Object visitExpressionPixelConstructor(
			ExpressionPixelConstructor expressionPixelConstructor, Object arg)
			throws Exception {
		expressionPixelConstructor.alpha.visit(this, arg);
		expressionPixelConstructor.red.visit(this, arg);
		expressionPixelConstructor.green.visit(this, arg);
		expressionPixelConstructor.blue.visit(this, arg);
		mv.visitMethodInsn(INVOKESTATIC, RuntimePixelOps.className, "makePixel", RuntimePixelOps.makePixelSig, false);
		return null;
	}

	@Override
	public Object visitExpressionPredefinedName(
			ExpressionPredefinedName expressionPredefinedName, Object arg)
			throws Exception {
		if (expressionPredefinedName.name == Kind.KW_default_width) {
			mv.visitLdcInsn(defaultWidth);
		}else if (expressionPredefinedName.name == Kind.KW_default_height) {
			mv.visitLdcInsn(defaultHeight);
		}else if (expressionPredefinedName.name == Kind.KW_Z) {
			mv.visitLdcInsn(Z);
		}
		return null;
	}

	@Override
	public Object visitExpressionUnary(ExpressionUnary expressionUnary,
			Object arg) throws Exception {
		if (expressionUnary.expression != null) expressionUnary.expression.visit(this, arg);

		if (expressionUnary.op == Kind.OP_PLUS) {} 
		else if (expressionUnary.op == Kind.OP_MINUS) {
			if (expressionUnary.expression.typeName == Type.INTEGER) mv.visitInsn(INEG);
			if (expressionUnary.expression.typeName == Type.FLOAT) mv.visitInsn(FNEG);
		} 
		else if (expressionUnary.op == Kind.OP_EXCLAMATION) {
			if (expressionUnary.expression.typeName == Type.INTEGER) {
				mv.visitLdcInsn(-1); 
				mv.visitInsn(IXOR);
			} else if (expressionUnary.expression.typeName == Type.BOOLEAN) {
				mv.visitLdcInsn(1); 
				mv.visitInsn(IXOR);
			}
		}
		return null;
	}

	@Override
	public Object visitLHSIdent(LHSIdent lhsIdent, Object arg) throws Exception {
		/* If the type is image, the value on top of the stack is actually a reference. Instead of copying the reference, a copy 
		 * of the image should be created and the refAerence to the copy stored. Use RuntimeImageSupport.deepCopy to copy the image.*/
		if(lhsIdent.dec.typeName == Type.IMAGE) {
			mv.visitMethodInsn(INVOKESTATIC, RuntimeImageSupport.className, "deepCopy", RuntimeImageSupport.deepCopySig, false);
			mv.visitVarInsn(ASTORE, lhsIdent.dec.getSlotNum());
		} else if(lhsIdent.dec.typeName == Type.INTEGER) {
			mv.visitVarInsn(ISTORE, lhsIdent.dec.getSlotNum());
		}
		else if(lhsIdent.dec.typeName == Type.FLOAT) {
			mv.visitVarInsn(FSTORE, lhsIdent.dec.getSlotNum());
		}
		else if(lhsIdent.dec.typeName == Type.BOOLEAN) {
			mv.visitVarInsn(ISTORE, lhsIdent.dec.getSlotNum());
		}
		else if(lhsIdent.dec.typeName == Type.FILE) mv.visitVarInsn(ASTORE, lhsIdent.dec.getSlotNum());
		return null;
	}

	@Override
	public Object visitLHSPixel(LHSPixel lhsPixel, Object arg)
			throws Exception {
	    mv.visitVarInsn(ALOAD, lhsPixel.dec.getSlotNum()); // load image
		if (lhsPixel.pixelSelector != null) lhsPixel.pixelSelector.visit(this, arg); // load x and y 
	    mv.visitMethodInsn(INVOKESTATIC, RuntimeImageSupport.className, "setPixel", RuntimeImageSupport.setPixelSig, false);
	    return null;
	}

	@Override
	public Object visitLHSSample(LHSSample lhsSample, Object arg)
			throws Exception {
		mv.visitVarInsn(ALOAD, lhsSample.dec.getSlotNum()); // load image
		if (lhsSample.pixelSelector != null) lhsSample.pixelSelector.visit(this, arg); // load x and y 
		if(lhsSample.color == Kind.KW_alpha) {
			mv.visitInsn(ICONST_0);
		} else if(lhsSample.color == Kind.KW_red) {
			mv.visitInsn(ICONST_1);
		} else if(lhsSample.color == Kind.KW_green) {
			mv.visitInsn(ICONST_2);
		} else if(lhsSample.color == Kind.KW_blue) {
			mv.visitInsn(ICONST_3);
		}
	    mv.visitMethodInsn(INVOKESTATIC, RuntimeImageSupport.className, "updatePixelColor", RuntimeImageSupport.updatePixelColorSig, false);
		return null;
	}

	@Override
	public Object visitPixelSelector(PixelSelector pixelSelector, Object arg)
			throws Exception {
		pixelSelector.ex.visit(this, arg);
//		  if (pixelSelector.ex.typeName.equals(Type.FLOAT)) {
//		   mv.visitInsn(F2D);
//		   mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "cos", "(D)D", false);
//		   mv.visitInsn(D2I);
//		  }
		pixelSelector.ey.visit(this, arg);
//		  if (pixelSelector.ey.typeName.equals(Type.FLOAT)) {
//		   mv.visitInsn(F2D);
//		   mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "sin", "(D)D", false);
//		   mv.visitInsn(D2I);
//		  }
		  return null;
	}

	@Override
	public Object visitProgram(Program program, Object arg) throws Exception {
		cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		// cw = new ClassWriter(0); //If the call to mv.visitMaxs(1, 1) crashes,
		// it is
		// sometime helpful to
		// temporarily run it without COMPUTE_FRAMES. You probably
		// won't get a completely correct classfile, but
		// you will be able to see the code that was
		// generated.
		className = program.progName;
		classDesc = "L" + className + ";";
		String sourceFileName = (String) arg;
		cw.visit(52, ACC_PUBLIC + ACC_SUPER, className, null, "java/lang/Object", null);
		cw.visitSource(sourceFileName, null);

		// create main method
		mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
		// initialize
		mv.visitCode();

		// add label before first instruction
		Label mainStart = new Label();
		mv.visitLabel(mainStart);
		
		CodeGenUtils.genLog(DEVEL, mv, "entering main");

		program.block.visit(this, arg);

		// generates code to add string to log
		CodeGenUtils.genLog(DEVEL, mv, "leaving main");

		// adds the required (by the JVM) return statement to main
		mv.visitInsn(RETURN);

		// adds label at end of code
		Label mainEnd = new Label();
		mv.visitLabel(mainEnd);
		mv.visitLocalVariable("args", "[Ljava/lang/String;", null, mainStart, mainEnd, 0);

		for(ASTNode decOrStatement: program.block.decsOrStatements) {
			if(decOrStatement instanceof Declaration) {
				//Visit local variables\
				Declaration dec = (Declaration) decOrStatement;
				mv.visitLocalVariable(dec.name, Types.getJVMType(dec.getTypeName()), null, mainStart, mainEnd, dec.getSlotNum());
			}
		}
		
		// labels 
		// Because we use ClassWriter.COMPUTE_FRAMES as a parameter in the
		// constructor,
		// asm will calculate this itself and the parameters are ignored.
		// If you have trouble with failures in this routine, it may be useful
		// to temporarily change the parameter in the ClassWriter constructor
		// from COMPUTE_FRAMES to 0.
		// The generated classfile will not be correct, but you will at least be
		// able to see what is in it.
		mv.visitMaxs(0, 0);

		// terminate construction of main method
		mv.visitEnd();

		// terminate class construction
		cw.visitEnd();

		// generate classfile as byte array and return
		return cw.toByteArray();
	}

	@Override
	public Object visitStatementAssign(StatementAssign statementAssign, Object arg) throws Exception {
		statementAssign.e.visit(this, arg);
		statementAssign.lhs.visit(this, arg);
		return statementAssign;
	}

	@Override
	public Object visitStatementIf(StatementIf statementIf, Object arg)
			throws Exception {
		Label after = new Label();
		statementIf.guard.visit(this, arg);
		mv.visitJumpInsn(IFEQ, after);
		statementIf.b.visit(this, arg);
		mv.visitLabel(after);
		return null;
	}

	@Override
	public Object visitStatementInput(StatementInput statementInput, Object arg) throws Exception {
		mv.visitVarInsn(ALOAD, 0);// ARGS arrays
		statementInput.e.visit(this, arg);
		mv.visitInsn(AALOAD); // load the arg array and index.
		/// integer, float boolean, filename, image
		if(statementInput.dec.typeName == Type.INTEGER) {
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
			mv.visitVarInsn(ISTORE, statementInput.dec.getSlotNum());
		} else if(statementInput.dec.typeName == Type.FLOAT) {
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "parseFloat", "(Ljava/lang/String;)F", false);
			mv.visitVarInsn(FSTORE, statementInput.dec.getSlotNum());
		} else if(statementInput.dec.typeName == Type.BOOLEAN) {
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z", false);
			mv.visitVarInsn(ISTORE, statementInput.dec.getSlotNum());
		} else if(statementInput.dec.typeName == Type.FILE) mv.visitVarInsn(ASTORE, statementInput.dec.getSlotNum());
		/* If the the type is image, the parameter is a url or file, and the image should be read from its location (invoke 
		 * RuntimeImageSupport.readImage). If a size was specified when the image variable was declared, the image should be resized 
		 * to this value. Otherwise, the image retains its original size.*/
		else if(statementInput.dec.typeName == Type.IMAGE) {
				if(statementInput.dec.width != null) {
					statementInput.dec.width.visit(this, null);
					mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf","(I)Ljava/lang/Integer;", false);
					statementInput.dec.height.visit(this, null);
					mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf","(I)Ljava/lang/Integer;", false);
				}else {
					mv.visitInsn(ACONST_NULL);	
				    mv.visitInsn(ACONST_NULL);
				}
				mv.visitMethodInsn(INVOKESTATIC, RuntimeImageSupport.className, "readImage", RuntimeImageSupport.readImageSig, false);
				// store in slot
				mv.visitVarInsn(ASTORE, statementInput.dec.getSlotNum());
		}
		return statementInput;
	}

	@Override
	public Object visitStatementShow(StatementShow statementShow, Object arg) throws Exception {
		/**
		 * refactor and complete implementation.
		 * 
		 * For integers, booleans, and floats, generate code to print to
		 * console. For images, generate code to display in a frame.
		 * 
		 * In all cases, invoke CodeGenUtils.genLogTOS(GRADE, mv, type); before
		 * consuming top of stack.
		 */
		statementShow.e.visit(this, arg);
		Type type = statementShow.e.getType();
		switch (type) {
			case INTEGER : {
				CodeGenUtils.genLogTOS(GRADE, mv, type);
				mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
				mv.visitInsn(Opcodes.SWAP);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
			}
				break;
			case BOOLEAN : {
				CodeGenUtils.genLogTOS(GRADE, mv, type);
				mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
				mv.visitInsn(Opcodes.SWAP);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Z)V", false);
			}
			 break; //commented out because currently unreachable. You will need
			// it.
			case FLOAT : {
				CodeGenUtils.genLogTOS(GRADE, mv, type);
				mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
				mv.visitInsn(Opcodes.SWAP);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(F)V", false);
			}
			 break;// commented out because currently unreachable. You will need
			// it.
			case IMAGE : {
				CodeGenUtils.genLogTOS(GRADE, mv, type);
				mv.visitMethodInsn(INVOKESTATIC, "cop5556sp18/RuntimeImageSupport", "makeFrame", RuntimeImageSupport.makeFrameSig, false);
				mv.visitInsn(POP);
			}
			break;
			default: break;
		}
		return null;
	}

	@Override
	public Object visitStatementSleep(StatementSleep statementSleep, Object arg)
			throws Exception {
		/*The value of the expression is the number of msecs that the program should sleep. Use java.lang.Thread.sleep.*/
		statementSleep.duration.visit(this, null);
		mv.visitInsn(I2L);
		mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "sleep", "(J)V", false);
		return null;
	}

	@Override
	public Object visitStatementWhile(StatementWhile statementWhile, Object arg)
			throws Exception {
		Label guard = new Label();
		Label body = new Label();
		mv.visitJumpInsn(GOTO, guard);
		mv.visitLabel(body);
		statementWhile.b.visit(this, arg); 
		mv.visitLabel(guard);
		statementWhile.guard.visit(this, arg);
		mv.visitJumpInsn(IFNE, body);
		return null;
	}

	@Override
	public Object visitStatementWrite(StatementWrite statementWrite, Object arg)
			throws Exception {
		mv.visitVarInsn(ALOAD, statementWrite.srcDec.slotNum);
		mv.visitVarInsn(ALOAD, statementWrite.destDec.slotNum);
		mv.visitMethodInsn(INVOKESTATIC, RuntimeImageSupport.className, "write", RuntimeImageSupport.writeSig, false);
		return null;
	}

}
