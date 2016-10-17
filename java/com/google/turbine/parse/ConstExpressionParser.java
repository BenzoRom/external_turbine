/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.turbine.parse;

import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.google.turbine.model.Const;
import com.google.turbine.model.TurbineConstantTypeKind;
import com.google.turbine.tree.Tree;
import com.google.turbine.tree.Tree.ClassLiteral;
import com.google.turbine.tree.Tree.ClassTy;
import com.google.turbine.tree.Tree.Expression;
import com.google.turbine.tree.Tree.VoidTy;
import com.google.turbine.tree.TurbineOperatorKind;
import javax.annotation.Nullable;

/** A parser for compile-time constant expressions. */
public class ConstExpressionParser {

  Token token;
  private final Lexer lexer;

  public ConstExpressionParser(Lexer lexer) {
    this.lexer = lexer;
    token = lexer.next();
  }

  private static TurbineOperatorKind operator(Token token) {
    switch (token) {
      case ASSIGN:
        // TODO(cushon): only allow in annotations?
        return TurbineOperatorKind.ASSIGN;
      case MULT:
        return TurbineOperatorKind.MULT;
      case DIV:
        return TurbineOperatorKind.DIVIDE;
      case MOD:
        return TurbineOperatorKind.MODULO;
      case PLUS:
        return TurbineOperatorKind.PLUS;
      case MINUS:
        return TurbineOperatorKind.MINUS;
      case LTLT:
        return TurbineOperatorKind.SHIFT_LEFT;
      case GTGT:
        return TurbineOperatorKind.SHIFT_RIGHT;
      case GTGTGT:
        return TurbineOperatorKind.UNSIGNED_SHIFT_RIGHT;
      case LT:
        return TurbineOperatorKind.LESS_THAN;
      case GT:
        return TurbineOperatorKind.GREATER_THAN;
      case LTE:
        return TurbineOperatorKind.LESS_THAN_EQ;
      case GTE:
        return TurbineOperatorKind.GREATER_THAN_EQ;
      case EQ:
        return TurbineOperatorKind.EQUAL;
      case NOTEQ:
        return TurbineOperatorKind.NOT_EQUAL;
      case AND:
        return TurbineOperatorKind.BITWISE_AND;
      case OR:
        return TurbineOperatorKind.BITWISE_OR;
      case XOR:
        return TurbineOperatorKind.BITWISE_XOR;
      case ANDAND:
        return TurbineOperatorKind.AND;
      case OROR:
        return TurbineOperatorKind.OR;
      case COND:
        return TurbineOperatorKind.TERNARY;
      default:
        return null;
    }
  }

  private Tree.Expression primary(boolean negate) {
    switch (token) {
      case INT_LITERAL:
        return finishLiteral(TurbineConstantTypeKind.INT, negate);
      case DOUBLE_LITERAL:
        return finishLiteral(TurbineConstantTypeKind.DOUBLE, negate);
      case LONG_LITERAL:
        return finishLiteral(TurbineConstantTypeKind.LONG, negate);
      case FLOAT_LITERAL:
        return finishLiteral(TurbineConstantTypeKind.FLOAT, negate);
      case TRUE:
        eat();
        return new Tree.Literal(TurbineConstantTypeKind.BOOLEAN, new Const.BooleanValue(true));
      case FALSE:
        eat();
        return new Tree.Literal(TurbineConstantTypeKind.BOOLEAN, new Const.BooleanValue(false));
      case CHAR_LITERAL:
        return finishLiteral(TurbineConstantTypeKind.CHAR, negate);
      case STRING_LITERAL:
        return finishLiteral(TurbineConstantTypeKind.STRING, false);
      case PLUS:
        eat();
        return unaryRest(TurbineOperatorKind.UNARY_PLUS);
      case MINUS:
        eat();
        return unaryRest(TurbineOperatorKind.NEG);
      case NOT:
        eat();
        return unaryRest(TurbineOperatorKind.NOT);
      case TILDE:
        eat();
        return unaryRest(TurbineOperatorKind.BITWISE_COMP);
      case LPAREN:
        return maybeCast();
      case LBRACE:
        eat();
        return arrayInitializer();
      case IDENT:
        return qualIdent();
      case BYTE:
        return primitiveClassLiteral(TurbineConstantTypeKind.BYTE);
      case CHAR:
        return primitiveClassLiteral(TurbineConstantTypeKind.CHAR);
      case DOUBLE:
        return primitiveClassLiteral(TurbineConstantTypeKind.DOUBLE);
      case FLOAT:
        return primitiveClassLiteral(TurbineConstantTypeKind.FLOAT);
      case INT:
        return primitiveClassLiteral(TurbineConstantTypeKind.INT);
      case LONG:
        return primitiveClassLiteral(TurbineConstantTypeKind.LONG);
      case SHORT:
        return primitiveClassLiteral(TurbineConstantTypeKind.SHORT);
      case BOOLEAN:
        return primitiveClassLiteral(TurbineConstantTypeKind.BOOLEAN);
      case VOID:
        return primitiveClassLiteral(VoidTy.INSTANCE);
      case AT:
        return annotation();
      default:
        return null;
    }
  }

  private Expression primitiveClassLiteral(TurbineConstantTypeKind type) {
    return primitiveClassLiteral(new Tree.PrimTy(type));
  }

  private Expression primitiveClassLiteral(Tree.Type type) {
    eat();
    if (token != Token.DOT) {
      return null;
    }
    eat();
    if (token != Token.CLASS) {
      return null;
    }
    eat();
    return new ClassLiteral(type);
  }

  private Tree.Expression maybeCast() {
    eat();
    switch (token) {
      case BOOLEAN:
        eat();
        return castTail(TurbineConstantTypeKind.BOOLEAN);
      case BYTE:
        eat();
        return castTail(TurbineConstantTypeKind.BYTE);
      case SHORT:
        eat();
        return castTail(TurbineConstantTypeKind.SHORT);
      case INT:
        eat();
        return castTail(TurbineConstantTypeKind.INT);
      case LONG:
        eat();
        return castTail(TurbineConstantTypeKind.LONG);
      case CHAR:
        eat();
        return castTail(TurbineConstantTypeKind.CHAR);
      case DOUBLE:
        eat();
        return castTail(TurbineConstantTypeKind.DOUBLE);
      case FLOAT:
        eat();
        return castTail(TurbineConstantTypeKind.FLOAT);
      default:
        return notCast();
    }
  }

  private Tree.Expression notCast() {
    Tree.Expression expr = expression(null);
    if (expr == null) {
      return null;
    }
    if (token != Token.RPAREN) {
      return null;
    }
    eat();
    if (expr.kind() == Tree.Kind.CONST_VAR_NAME) {
      Tree.ConstVarName cvar = (Tree.ConstVarName) expr;
      switch (token) {
        case INT_LITERAL:
        case FLOAT_LITERAL:
        case TRUE:
        case FALSE:
        case CHAR_LITERAL:
        case STRING_LITERAL:
        case NOT:
        case TILDE:
        case IDENT:
          {
            return new Tree.TypeCast(asClassTy(cvar.name()), primary(false));
          }
        default:
          return expr;
      }
    } else {
      return expr;
    }
  }

  private ClassTy asClassTy(ImmutableList<String> names) {
    ClassTy cty = null;
    for (String bit : names) {
      cty = new ClassTy(Optional.fromNullable(cty), bit, ImmutableList.<Tree.Type>of());
    }
    return cty;
  }

  private void eat() {
    token = lexer.next();
  }

  private Tree.Expression arrayInitializer() {
    if (token == Token.RBRACE) {
      eat();
      return new Tree.ArrayInit(ImmutableList.<Tree.Expression>of());
    }

    ImmutableList.Builder<Tree.Expression> exprs = ImmutableList.builder();
    OUTER:
    while (true) {
      if (token == Token.RBRACE) {
        eat();
        break OUTER;
      }
      Tree.Expression item = expression(null);
      if (item == null) {
        return null;
      }
      exprs.add(item);
      switch (token) {
        case COMMA:
          eat();
          break;
        case RBRACE:
          eat();
          break OUTER;
        default:
          return null;
      }
    }
    return new Tree.ArrayInit(exprs.build());
  }

  /** Finish hex, decimal, octal, and binary integer literals (see JLS 3.10.1). */
  private Tree.Expression finishLiteral(TurbineConstantTypeKind kind, boolean negate) {
    String text = lexer.stringValue();
    Const.Value value;
    switch (kind) {
      case INT:
        {
          int radix = 10;
          if (text.startsWith("0x") || text.startsWith("0X")) {
            text = text.substring(2);
            radix = 0x10;
          } else if (text.startsWith("0")
              && text.length() > 1
              && Character.isDigit(text.charAt(1))) {
            radix = 010;
          } else if (text.startsWith("0b") || text.startsWith("0B")) {
            text = text.substring(2);
            radix = 0b10;
          }
          if (negate) {
            text = "-" + text;
          }
          long longValue = parseLong(text, radix);
          value =
              new Const.IntValue(
                  radix != 10 && longValue == 0xffffffffL ? -1 : Ints.checkedCast(longValue));
          break;
        }
      case LONG:
        {
          int radix = 10;
          if (text.startsWith("0x") || text.startsWith("0X")) {
            text = text.substring(2);
            radix = 0x10;
          } else if (text.startsWith("0")
              && text.length() > 1
              && Character.isDigit(text.charAt(1))) {
            radix = 010;
          } else if (text.startsWith("0b") || text.startsWith("0B")) {
            text = text.substring(2);
            radix = 0b10;
          }
          if (negate) {
            text = "-" + text;
          }
          if (text.endsWith("L") || text.endsWith("l")) {
            text = text.substring(0, text.length() - 1);
          }
          value = new Const.LongValue(parseLong(text, radix));
          break;
        }
      case CHAR:
        value = new Const.CharValue(text.charAt(0));
        break;
      case FLOAT:
        value = new Const.FloatValue(Float.parseFloat(text));
        break;
      case DOUBLE:
        value = new Const.DoubleValue(Double.parseDouble(text));
        break;
      case STRING:
        value = new Const.StringValue(text);
        break;
      default:
        throw error(kind);
    }
    eat();
    return new Tree.Literal(kind, value);
  }

  /**
   * Parse the string as a signed long.
   *
   * <p>{@link Long#parseLong} doesn't accept {@link Long#MIN_VALUE}.
   */
  private long parseLong(String text, int radix) {
    long r = 0;
    boolean neg = text.startsWith("-");
    if (neg) {
      text = text.substring(1);
    }
    for (char c : text.toCharArray()) {
      int digit;
      if ('0' <= c && c <= '9') {
        digit = c - '0';
      } else if ('a' <= c && c <= 'f') {
        digit = 10 + (c - 'a');
      } else if ('A' <= c && c <= 'F') {
        digit = 10 + (c - 'A');
      } else if (c == '_') {
        continue;
      } else {
        throw error(text);
      }
      r = (r * radix) + digit;
    }
    if (neg) {
      r = -r;
    }
    return r;
  }

  private Tree.Expression unaryRest(TurbineOperatorKind op) {
    boolean negate = op == TurbineOperatorKind.NEG;
    Tree.Expression expr = primary(negate);
    if (negate && expr.kind() == Tree.Kind.LITERAL) {
      Tree.Literal lit = (Tree.Literal) expr;
      switch (lit.tykind()) {
        case INT:
        case LONG:
          return expr;
        default:
          break;
      }
    }
    if (expr == null) {
      return null;
    }
    return new Tree.Unary(expr, op);
  }

  @Nullable
  private Tree.Expression qualIdent() {
    ImmutableList.Builder<String> bits = ImmutableList.builder();
    bits.add(lexer.stringValue());
    eat();
    while (token == Token.DOT) {
      eat();
      switch (token) {
        case IDENT:
          bits.add(lexer.stringValue());
          break;
        case CLASS:
          // TODO(cushon): only allow in annotations?
          eat();
          return new Tree.ClassLiteral(asClassTy(bits.build()));
        default:
          return null;
      }
      eat();
    }
    return new Tree.ConstVarName(bits.build());
  }

  public Tree.Expression expression() {
    Tree.Expression result = expression(null);
    switch (token) {
      case EOF:
      case SEMI:
        // TODO(cushon): only allow in annotations?
      case COMMA:
      case RPAREN:
        return result;
      default:
        return null;
    }
  }

  private Tree.Expression expression(TurbineOperatorKind.Precedence prec) {
    Tree.Expression term1 = primary(false);
    if (term1 == null) {
      return null;
    }
    return expression(term1, prec);
  }

  private Tree.Expression expression(Tree.Expression term1, TurbineOperatorKind.Precedence prec) {
    while (true) {
      if (token == Token.EOF) {
        return term1;
      }
      TurbineOperatorKind op = operator(token);
      if (op == null) {
        return term1;
      }
      if (prec != null && op.prec().rank() <= prec.rank()) {
        return term1;
      }
      eat();
      if (op == TurbineOperatorKind.TERNARY) {
        term1 = ternary(term1);
      } else if (op == TurbineOperatorKind.ASSIGN) {
        term1 = assign(term1, op);
      } else {
        term1 = new Tree.Binary(term1, expression(op.prec()), op);
      }
      if (term1 == null) {
        return null;
      }
    }
  }

  private Tree.Expression assign(Tree.Expression term1, TurbineOperatorKind op) {
    if (!(term1 instanceof Tree.ConstVarName)) {
      return null;
    }
    ImmutableList<String> names = ((Tree.ConstVarName) term1).name();
    if (names.size() > 1) {
      return null;
    }
    String name = getOnlyElement(names);
    Tree.Expression rhs = expression(op.prec());
    return new Tree.Assign(name, rhs);
  }

  private Tree.Expression ternary(Tree.Expression term1) {
    Tree.Expression thenExpr = expression(TurbineOperatorKind.Precedence.TERNARY);
    if (thenExpr == null) {
      return null;
    }
    if (token != Token.COLON) {
      return null;
    }
    eat();
    Tree.Expression elseExpr = expression(TurbineOperatorKind.Precedence.TERNARY);
    if (elseExpr == null) {
      return null;
    }
    return new Tree.Conditional(term1, thenExpr, elseExpr);
  }

  private Tree.Expression castTail(TurbineConstantTypeKind ty) {
    if (token != Token.RPAREN) {
      return null;
    }
    eat();
    Tree.Expression rhs = primary(false);
    if (rhs == null) {
      throw new AssertionError();
    }
    return new Tree.TypeCast(new Tree.PrimTy(ty), rhs);
  }

  private Tree.AnnoExpr annotation() {
    if (token != Token.AT) {
      throw new AssertionError();
    }
    eat();
    Tree.Expression name = qualIdent();
    ImmutableList.Builder<Tree.Expression> args = ImmutableList.builder();
    if (token == Token.LPAREN) {
      eat();
      while (true) {
        Tree.Expression expression = expression();
        if (expression == null) {
          throw new AssertionError("invalid annotation expression");
        }
        args.add(expression);
        if (token == Token.COMMA) {
          eat();
          continue;
        }
        break;
      }
      if (token == Token.RPAREN) {
        eat();
      }
    }
    // TODO(cushon): avoid stringifying names
    return new Tree.AnnoExpr(new Tree.Anno(name.toString(), args.build()));
  }

  private ParseError error(Object message) {
    return new ParseError(lexer.position(), String.valueOf(message));
  }
}
