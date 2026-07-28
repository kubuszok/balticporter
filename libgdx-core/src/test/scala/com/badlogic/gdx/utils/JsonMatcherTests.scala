package com.badlogic.gdx.utils

class JsonMatcherTests extends munit.FunSuite {
  var watcher: org.junit.rules.TestWatcher = new org.junit.rules.TestWatcher() {
    override def failed(cause: java.lang.Throwable, desc: org.junit.runner.Description): scala.Unit = {
      val sw: java.io.StringWriter = new java.io.StringWriter()
      val pw: java.io.PrintWriter = new java.io.PrintWriter(sw)
      cause.printStackTrace(pw)
      val trimmed: java.lang.String = sw.toString().replace("\t", "   ").replaceAll("^[^:]*\\.([^:]+?): ", "[$1] ").lines().filter((line: java.lang.String) => {
        val stripped: java.lang.String = line.stripLeading()
        return (stripped.isEmpty() || (!stripped.startsWith("at "))) || stripped.startsWith("at com.badlogic.gdx")
      }).collect(java.util.stream.Collectors.joining("\n"))
      java.lang.System.out.println(((("--- " + desc.getTestClass().getSimpleName()) + ": ") + desc.getMethodName()) + " ---")
      java.lang.System.out.println(trimmed)
      java.lang.System.out.println()
    }
  }
  test("singlePatterns")({
    JsonMatcherTests.test(JsonMatcherTests.json, "*/(type)", scala.Array[java.lang.String]("ENCHARGE"))
    JsonMatcherTests.test(JsonMatcherTests.json, "*@/(type)", scala.Array[java.lang.String]("ENCHARGE", "ENPOWER"))
    JsonMatcherTests.test(JsonMatcherTests.json, "*/devices/*/(serial_num,percentFull)", scala.Array[java.lang.String]("{serial_num:32131444,percentFull:100}"))
    JsonMatcherTests.test("[{a:[{deep:value}]}]", "*/*/*/(deep)", scala.Array[java.lang.String]("value"))
    JsonMatcherTests.test("{root:{a:1,b:2}}", "root/(a)/*", scala.Array[java.lang.String]("1"))
    JsonMatcherTests.test(JsonMatcherTests.json, "*/(type)", scala.Array[java.lang.String]("ENCHARGE"))
    JsonMatcherTests.test(JsonMatcherTests.json, "*/devices/**/(value)", scala.Array[java.lang.String]("1"))
    JsonMatcherTests.test("{a:{b:{value:1}},c:{value:2},value:3}", "**/(value)", scala.Array[java.lang.String]("1"))
    JsonMatcherTests.test("{servers:{prod:{config:{port:8080}}},config:{port:9090}}", "**/config/(port)", scala.Array[java.lang.String]("8080"))
    JsonMatcherTests.test(JsonMatcherTests.json, "*/devices/*@/(serial_num,percentFull)", scala.Array[java.lang.String]("{serial_num:32131444,percentFull:100}", "{percentFull:75,serial_num:234234211}", "{serial_num:9834711}"))
    JsonMatcherTests.test("{outer:[{id:1,inner:[{name:A},{name:B}]}]}", "outer/*@/(id),inner@/*/(name[])", scala.Array[java.lang.String]("1", "[A,B]"))
    JsonMatcherTests.test("{data:[{items:[{value:1},{value:2}]}]}", "data/*@/items/*/(value)", scala.Array[java.lang.String]("2"))
    JsonMatcherTests.test("{data:[{items:[{value:1},{value:2}]}]}", "data/*/items/*@/(value)", scala.Array[java.lang.String]("1", "2"))
    JsonMatcherTests.test(JsonMatcherTests.json, "*/devices/**@/(value)", scala.Array[java.lang.String]("1", "2"))
    JsonMatcherTests.test("{id:1,child:{id:2,nested:{id:3}}}", "**@/(id)", scala.Array[java.lang.String]("1", "2", "3"))
    JsonMatcherTests.test("{config:{version:1.0,debug:{level:info},name:MyApp}}", "config/(name)", scala.Array[java.lang.String]("MyApp"))
    JsonMatcherTests.test("{servers:{prod:{host:prod.example.com},dev:{host:dev.example.com}}}", "servers/prod/(host)", scala.Array[java.lang.String]("prod.example.com"))
    JsonMatcherTests.test("{data:{item1:{nested:{deep:true},id:first},item2:{id:second}}}", "data/*/(id)", scala.Array[java.lang.String]("first"))
    JsonMatcherTests.test("{}", "test/(value)", scala.Array[java.lang.String]())
    JsonMatcherTests.test("[]", "*/(name)", scala.Array[java.lang.String]())
    JsonMatcherTests.test("{a:{b:{c:{d:{e:{f:{value:deep}}}}}}}", "a/b/c/d/e/f/(value)", scala.Array[java.lang.String]("deep"))
    JsonMatcherTests.test("{data:[[{id:1},{id:2}],[{id:3}]]}", "data/*/*/(id)", scala.Array[java.lang.String]("1"))
    JsonMatcherTests.test("{empty:[{},{}]}", "empty/(*@)", scala.Array[java.lang.String]("{}", "{}"))
    JsonMatcherTests.test("{empty:[{},{}]}", "empty/(*)", scala.Array[java.lang.String]("{}"))
    JsonMatcherTests.test("{users:{johnny:{age:30}}}", "users/john/(hair)", scala.Array[java.lang.String]())
    JsonMatcherTests.test("{users:{johnny:{age:30}}}", "users/john/*/(age)", scala.Array[java.lang.String]())
    JsonMatcherTests.test("{data:{item1:{nested:{deep:true},id:first},item2:{id:second}}}", "data/(deep)", scala.Array[java.lang.String]())
    JsonMatcherTests.test("{other:{path:{value:test}}}", "nonexistent/path/(value)", scala.Array[java.lang.String]())
    JsonMatcherTests.test("{outer:{name:A,inner:{name:B}}}", "outer/(name),inner/(name)", scala.Array[java.lang.String]("{name:B}"))
    JsonMatcherTests.test("{data:{field.with.dots:1,field-with-dashes:2,field_with_underscores:3}}", "data/(field.with.dots,field-with-dashes,field_with_underscores)", scala.Array[java.lang.String]("{field.with.dots:1,field-with-dashes:2,field_with_underscores:3}"))
    JsonMatcherTests.test("{data:{z:1,x:{ok:true},items:[a,b,c]}}", "data/items/(*@)", scala.Array[java.lang.String]("a", "b", "c"))
    JsonMatcherTests.test("{data:{z:1,x:{ok:true},items:[a,b,c]}}", "data/(items)", scala.Array[java.lang.String]("[a,b,c]"))
    JsonMatcherTests.test("{data:{items:[a,b,c]}}", "data/(items[])", scala.Array[java.lang.String]("[[a,b,c]]"))
    JsonMatcherTests.test("{data:{empty:[]}}", "data/(empty)", scala.Array[java.lang.String]("[]"))
    JsonMatcherTests.test("{items:[{tags:[red,big]},{tags:[blue]}]}", "items/(*@)", scala.Array[java.lang.String]("{tags:[red,big]}", "{tags:[blue]}"))
    JsonMatcherTests.test("{items:[{tags:[red,big]},{tags:[blue]}]}", "items/*@/(tags)", scala.Array[java.lang.String]("[red,big]", "[blue]"))
    JsonMatcherTests.test(JsonMatcherTests.json, "*/devices/*/(device_status)", scala.Array[java.lang.String]("[envoy.global.ok,prop.done]"))
    JsonMatcherTests.test(JsonMatcherTests.json, "*/devices/*@/(device_status)", scala.Array[java.lang.String]("[envoy.global.ok,prop.done]", "[envoy.global.failure,prop.waiting]"))
    JsonMatcherTests.test(JsonMatcherTests.json, "*/devices/*/device_status/(*)", scala.Array[java.lang.String]("envoy.global.ok"))
    JsonMatcherTests.test(JsonMatcherTests.json, "*/devices/*/device_status/(*[])", scala.Array[java.lang.String]("[envoy.global.ok,prop.done,envoy.global.failure,prop.waiting]"))
    JsonMatcherTests.test(JsonMatcherTests.json, "*/devices/*/device_status/(*@)", scala.Array[java.lang.String]("envoy.global.ok", "prop.done", "envoy.global.failure", "prop.waiting"))
    JsonMatcherTests.test("{items:[{tags:[red,big]},{tags:[blue]}]}", "items/*/(tags[])", scala.Array[java.lang.String]("[[red,big],[blue]]"))
    JsonMatcherTests.test("{items:[{tags:[red,big]},{tags:[blue]}]}", "**@/(tags)", scala.Array[java.lang.String]("[red,big]", "[blue]"))
    JsonMatcherTests.test("{items:[{tags:[{a:red},{b:big}]},{tags:[{c:blue}]}]}", "items/*/(tags[])", scala.Array[java.lang.String]("[[{a:red},{b:big}],[{c:blue}]]"))
    JsonMatcherTests.test("{items:[{tags:[{a:red},{b:big}]},{tags:[{c:blue}]}]}", "**@/(tags)", scala.Array[java.lang.String]("[{a:red},{b:big}]", "[{c:blue}]"))
    JsonMatcherTests.test("{items:[{tags:[red,big]},{tags:[blue]}]}", "**/tags/(*@)", scala.Array[java.lang.String]("red", "big", "blue"))
    JsonMatcherTests.test("{items:[{tags:[red,big]},{tags:[blue]}]}", "*/*/(*@)", scala.Array[java.lang.String]("[red,big]", "[blue]"))
    JsonMatcherTests.test("[{tags:[red,big]},{tags:[blue]}]", "(*@)", scala.Array[java.lang.String]("{tags:[red,big]}", "{tags:[blue]}"))
    JsonMatcherTests.test("{tags1:[red,big],tags2:[blue]}", "(*@)", scala.Array[java.lang.String]("[red,big]", "[blue]"))
    JsonMatcherTests.test("{items1:{tags:[red,big]},items2:{tags:[blue]}}", "(*@)", scala.Array[java.lang.String]("{tags:[red,big]}", "{tags:[blue]}"))
    JsonMatcherTests.test("{data:{a:[1,2],b:[3,4],c:[5,6]}}", "data/(a,b,c)", scala.Array[java.lang.String]("{a:[1,2],b:[3,4],c:[5,6]}"))
    JsonMatcherTests.test("{a:{x:1,y:2},b:{x:3,y:4}}", "*/(x,y[])", scala.Array[java.lang.String]("{x:3,y:[2,4]}"))
    JsonMatcherTests.test("{a:{val:1},b:{c:{val:2},d:{val:3}}}", "**@/(val[])", scala.Array[java.lang.String]("[1]", "[2]", "[3]"))
    JsonMatcherTests.test("{a:{a:{a:{a:{a:{x:deep}}}}}}", "**/(x)", scala.Array[java.lang.String]("deep"))
    JsonMatcherTests.test("{items:[{deep:{a:{x:1},b:{x:2}}}]}", "items/*@/deep/**@/(x)", scala.Array[java.lang.String]("1", "2"))
    JsonMatcherTests.test("{x:1,a:{x:2},b:{c:{x:3}}}", "**/(x[])", scala.Array[java.lang.String]("[1,2,3]"))
    JsonMatcherTests.test("{tags:[red,blue,green]}", "tags/(*@)", scala.Array[java.lang.String]("red", "blue", "green"))
    JsonMatcherTests.test("{matrix:[[1,2],[3,4]]}", "matrix/*/(*@)", scala.Array[java.lang.String]("1", "2", "3", "4"))
    JsonMatcherTests.test("[{id:1,name:A},{id:2,name:B}]", "*@/(id,name)", scala.Array[java.lang.String]("{id:1,name:A}", "{id:2,name:B}"))
    JsonMatcherTests.test("{a:{b:{target:{x:1}},c:{target:{x:2}}}}", "**/target/(x[])", scala.Array[java.lang.String]("[1,2]"))
    JsonMatcherTests.test("{data:{a:{val:1},b:{val:2},c:{val:3}}}", "data/*/(*@)", scala.Array[java.lang.String]("1", "2", "3"))
    JsonMatcherTests.test("{data:'He said \\\"hello\\\"'}", "(data)", scala.Array[java.lang.String]("'He said \"hello\"'"))
    JsonMatcherTests.test("{list:[{items:[a,b]},{items:[c,d]}]}", "list/*@/items/(*@)", scala.Array[java.lang.String]("a", "b", "c", "d"))
    JsonMatcherTests.test("{a:{b:{c:{d:1}}},x:{b:{c:{d:2}}}}", "*/b/**/(d)", scala.Array[java.lang.String]("1"))
    JsonMatcherTests.test("{a:{b:{c:{d:1}}},x:{b:{c:{d:2}}}}", "*/b/**/(c)", scala.Array[java.lang.String]("{d:1}"))
    JsonMatcherTests.test("{a:{b:{c:{d:1}}},x:{b:{c:{d:2}}}}", "*/b/**/(c[])", scala.Array[java.lang.String]("[{d:1},{d:2}]"))
    JsonMatcherTests.test("{data:{name:null,value:123}}", "data/(name,value)", scala.Array[java.lang.String]("{name:null,value:123}"))
    JsonMatcherTests.test("{items:[null,a,null,b]}", "items/(*@)", scala.Array[java.lang.String](null.asInstanceOf[java.lang.String], "a", null, "b"))
    JsonMatcherTests.test("{data:[{x:null},{x:1},{x:null}]}", "data/*/(x[])", scala.Array[java.lang.String]("[null,1,null]"))
    JsonMatcherTests.test("{config:{debug:true,port:8080,ratio:3.14}}", "config/(debug,port,ratio)", scala.Array[java.lang.String]("{debug:true,port:8080,ratio:3.14}"))
    JsonMatcherTests.test("{\"\":empty,normal:value}", "('')", scala.Array[java.lang.String]("empty"))
    JsonMatcherTests.test("{\"\":empty,normal:value}", "('',normal)", scala.Array[java.lang.String]("{\"\":empty,normal:value}"))
    JsonMatcherTests.test("{user:{名前:太郎,età:25,émoji:🚀}}", "user/(名前,età,émoji)", scala.Array[java.lang.String]("{名前:太郎,età:25,émoji:🚀}"))
    JsonMatcherTests.test("{a:{b:{c:{d:{e:{f:{g:{h:{i:{j:{value:deep}}}}}}}}}}}", "a/b/c/d/e/f/g/h/i/j/(value)", scala.Array[java.lang.String]("deep"))
    JsonMatcherTests.test("{a:{x:1,b:{x:2,c:{x:3}}}}", "**/(x)", scala.Array[java.lang.String]("1"))
    JsonMatcherTests.test("{a:{b:{c:{d:{e:value}}}}}", "**/b/**/d/(e)", scala.Array[java.lang.String]("value"))
    JsonMatcherTests.test("{data:[{items:[1,2]},{items:[3,4]}]}", "data/*@/items/(*@)", scala.Array[java.lang.String]("1", "2", "3", "4"))
    JsonMatcherTests.test("{data:[{a:1},{b:2}],meta:{items:[{c:3},{d:4}]}}", "*/*/*@/(*)", scala.Array[java.lang.String]("3", "4"))
    JsonMatcherTests.test("{data:[{a:1},{b:2}],meta:{items:[{c:3},{d:4,e:5}]}}", "*/*/*@/(*[])", scala.Array[java.lang.String]("[3]", "[4,5]"))
    JsonMatcherTests.test("{\"123\":{\"456\":value,normal:other}}", "123/(456,normal)", scala.Array[java.lang.String]("{456:value,normal:other}"))
    JsonMatcherTests.test("{\"  field \n \":{\" \t nested \t\":value}}", "  field \n /( \t nested \t)", scala.Array[java.lang.String]("value"))
    JsonMatcherTests.test("{\"  field \n \":{\" \t nested \t\":value}}", "(  field \n )", scala.Array[java.lang.String]("{\" \\t nested \\t\":value}"))
    JsonMatcherTests.test("{a:1,b:{c:2,d:{e:3}}}", "**@/(*)", scala.Array[java.lang.String]("1", "{c:2,d:{e:3}}"))
    JsonMatcherTests.test("{data:[[[{x:1}]],[[{x:2}],[{x:3}]]]}", "data/*/*/*/(x)", scala.Array[java.lang.String]("1"))
    JsonMatcherTests.test("{data:[[[{x:1}]],[[{x:2}],[{x:3}]]]}", "data/*/*/*/(x[])", scala.Array[java.lang.String]("[1,2,3]"))
    JsonMatcherTests.test("{empty:[]}", "empty/(*@)", scala.Array[java.lang.String]())
    JsonMatcherTests.test("{empty:{}}", "empty/(*)", scala.Array[java.lang.String]())
    JsonMatcherTests.test("{user:{profile:{name:John,age:30}}}", "user/profile/(name)", scala.Array[java.lang.String]("John"))
    JsonMatcherTests.test("{user:{profile:{name:John,age:30}}}", "user/profile/(name[])", scala.Array[java.lang.String]("[John]"))
    JsonMatcherTests.test("{a:{val:1,b:{val:2,c:{val:3}}}}", "a/**@/(val)", scala.Array[java.lang.String]("1", "2", "3"))
    JsonMatcherTests.test(("{items:[" + "1,".repeat(99)) + "100]}", "items/(*[])", scala.Array[java.lang.String](("[" + "1,".repeat(99)) + "100]"))
    JsonMatcherTests.test("{list:[{user:{name:A}},{user:{name:B}}]}", "list/*@/user/(name)", scala.Array[java.lang.String]("A", "B"))
    JsonMatcherTests.test("{deep:{nested:{items:[a,b,c]}}}", "**/items/(*@)", scala.Array[java.lang.String]("a", "b", "c"))
    JsonMatcherTests.test("{name:root,child:{name:nested}}", "(name),child/(name)", scala.Array[java.lang.String]("{name:nested}"))
    JsonMatcherTests.test("{a:{x:[1,2]},b:{c:{x:[3,4]}}}", "**/(x[])", scala.Array[java.lang.String]("[[1,2],[3,4]]"))
    JsonMatcherTests.test("{}", "(*)", scala.Array[java.lang.String]())
    JsonMatcherTests.test("{users:{john:{age:30},jane:{age:25}}}", "users/*@/(age)", scala.Array[java.lang.String]("30", "25"))
    JsonMatcherTests.test("{left:[{x:1},{x:2}],right:[{x:3},{x:4}]}", "*/*@/(x)", scala.Array[java.lang.String]("1", "2", "3", "4"))
    JsonMatcherTests.test("42", "(value)", scala.Array[java.lang.String]())
    JsonMatcherTests.test("\"hello\"", "(*)", scala.Array[java.lang.String]("hello"))
    JsonMatcherTests.test("{skip:{this:{find:{me:{x:1}}}}}", "skip/**/find/**@/(x)", scala.Array[java.lang.String]("1"))
    JsonMatcherTests.test("{\"field\nwith\nnewlines\":1,\"field\twith\ttabs\":2}", "(field\nwith\nnewlines,field\twith\ttabs)", scala.Array[java.lang.String]("{field\\nwith\\nnewlines:1,field\\twith\\ttabs:2}"))
    JsonMatcherTests.test("{mixed:[1,string,true,null,{obj:value}]}", "mixed/(*@)", scala.Array[java.lang.String]("1", "string", "true", null, "{obj:value}"))
    JsonMatcherTests.test("{a:{b:{c:{x:1,d:{x:2}}}}}", "**@/b/**/(x),d/(x)", scala.Array[java.lang.String]("1", "2"))
    JsonMatcherTests.test("{a:{b:{c:{x:1,d:{x:2}}}}}", "**@/b/**@/(x),d/(x)", scala.Array[java.lang.String]("1", "2"))
    JsonMatcherTests.test("[{a:{b:{x:1,bb:{c:{x:2}}}}},{a:{b:{x:3,bb:{c:{x:4}}}}}]", "**@/b/(x),*/**/c/(x)", scala.Array[java.lang.String]("1", "2", "3", "4"))
    JsonMatcherTests.test("{a:{b:{x:1,bb:{c:{x:2,d:{x:3}}}}}}", "**/b/(x),bb/**@/c/(x),d/(x)", scala.Array[java.lang.String]("1", "2", "3"))
    JsonMatcherTests.test("{data:{a:{deep:1},b:{deep:2}}}", "(*)", scala.Array[java.lang.String]("{a:{deep:1},b:{deep:2}}"))
    JsonMatcherTests.test("{data:{a:{deep:1},b:{deep:2}}}", "data/(*)", scala.Array[java.lang.String]("{deep:1}"))
    JsonMatcherTests.test("{data:{a:{deep:1},b:{deep:2}}}", "data/(*[])", scala.Array[java.lang.String]("[{deep:1},{deep:2}]"))
    JsonMatcherTests.test("{data:{a:{deep:1},b:{deep:2}}}", "data/(*@)", scala.Array[java.lang.String]("{deep:1}", "{deep:2}"))
    JsonMatcherTests.test("{list:[{id:1,data:{x:10}},{id:2,data:{x:20}}]}", "list/*@/(data)", scala.Array[java.lang.String]("{x:10}", "{x:20}"))
    JsonMatcherTests.test("{a:{b:{c:value}}}", "*/*/(c)", scala.Array[java.lang.String]("value"))
    JsonMatcherTests.test("{items:[1,2,3]}", "**@/(items)", scala.Array[java.lang.String]("[1,2,3]"))
    JsonMatcherTests.test("{root:[{level1:[{level2:[{value:deep}]}]}]}", "root/*@/level1/*@/level2/*@/(value)", scala.Array[java.lang.String]("deep"))
    JsonMatcherTests.test("{items:[1,{x:2},3,{x:4}]}", "items/*@/(x)", scala.Array[java.lang.String]("2", "4"))
    JsonMatcherTests.test("{a:{b:{c:1}},d:{e:{f:2}}}", "x/y/(z)", scala.Array[java.lang.String]())
    JsonMatcherTests.test("{a:{b:{target:miss},c:{d:{target:{hit:true}}}}}", "**/d/target/(hit)", scala.Array[java.lang.String]("true"))
    JsonMatcherTests.test("{\"0\":first,\"1\":second,\"2\":third}", "(0,1,2)", scala.Array[java.lang.String]("{0:first,1:second,2:third}"))
    JsonMatcherTests.test("{items:[{},{},{}]}", "items/*@/(*)", scala.Array[java.lang.String]())
    JsonMatcherTests.test(("{\"" + "x".repeat(100)) + "\":value}", ("(" + "x".repeat(100)) + ")", scala.Array[java.lang.String]("value"))
    JsonMatcherTests.test("{level1:[{level2:[{x:1},{x:2}]}]}", "**@/level2/*@/(x)", scala.Array[java.lang.String]("1", "2"))
    JsonMatcherTests.test("{data:{\"length\":3,\"0\":a,\"1\":b,\"2\":c}}", "data/(length,0,1,2)", scala.Array[java.lang.String]("{length:3,0:a,1:b,2:c}"))
    JsonMatcherTests.test("{data:{value:\"line1\\nline2\\ttab\"}}", "data/(value)", scala.Array[java.lang.String]("line1\nline2\ttab"))
    JsonMatcherTests.test("[{a:{b:[1,2]}},{a:{b:[3,4]}}]", "*@/a/b/(*)", scala.Array[java.lang.String]("2", "4"))
    JsonMatcherTests.test("[{a:{b:[1,2]}},{a:{b:[3,4]}}]", "*@/a/b/(*[])", scala.Array[java.lang.String]("[1,2]", "[3,4]"))
    JsonMatcherTests.test("{x:[1,2],y:[2,3],z:[3,4]}", "(x,y,z)", scala.Array[java.lang.String]("{x:[1,2],y:[2,3],z:[3,4]}"))
    JsonMatcherTests.test("{a:1,b:{c:2,d:{e:3}}}", "**/(*)", scala.Array[java.lang.String]("1"))
    JsonMatcherTests.test("{a:1,b:{c:2,d:{e:3}}}", "**/(*[])", scala.Array[java.lang.String]("[1,{c:2,d:{e:3}}]"))
    JsonMatcherTests.test("{a:1,b:{c:2,d:{e:3}}}", "**/(*@)", scala.Array[java.lang.String]("1", "{c:2,d:{e:3}}"))
    JsonMatcherTests.test("{a:1,b:{c:2,d:{e:3}}}", "**@/(*)", scala.Array[java.lang.String]("1", "{c:2,d:{e:3}}"))
    JsonMatcherTests.test("{a:1,b:{c:2,d:{e:3}}}", "**@/(*@)", scala.Array[java.lang.String]("1", "{c:2,d:{e:3}}"))
    JsonMatcherTests.test("{node:{value:1,node:{value:2,node:{value:3}}}}", "**/node/(value)", scala.Array[java.lang.String]("1"))
    JsonMatcherTests.test("{user:1,userName:2,userAge:3}", "(user,userName,userAge)", scala.Array[java.lang.String]("{user:1,userName:2,userAge:3}"))
    JsonMatcherTests.test("{flags:{active:false,debug:false,enabled:true}}", "flags/(active,debug,enabled)", scala.Array[java.lang.String]("{active:false,debug:false,enabled:true}"))
    JsonMatcherTests.test("{user:{name:John},profile:{name:Jane}}", "user/(name)", scala.Array[java.lang.String]("John"))
    JsonMatcherTests.test("{x:1,nested:{x:2,deep:{x:3}}}", "(x),nested/(x)", scala.Array[java.lang.String]("{x:2}"))
    JsonMatcherTests.test("{data:{id:1,user:{id:2}}}", "data/(id),user/(id)", scala.Array[java.lang.String]("{id:2}"))
    JsonMatcherTests.test("{a:{x:1},b:{x:2}}", "*/(x)", scala.Array[java.lang.String]("1"))
    JsonMatcherTests.test("{items:[{x:1},{x:2},{x:3}]}", "items/*/(x)", scala.Array[java.lang.String]("1"))
    JsonMatcherTests.test("{level1:{name:A,level2:{name:B}},other:{name:C}}", "*/(name)", scala.Array[java.lang.String]("A"))
    JsonMatcherTests.test("{first:{x:1,second:{x:2}}}", "first/(x[]),second/(x[])", scala.Array[java.lang.String]("{x:[1,2]}"))
    JsonMatcherTests.test("{data:{a:{id:1},b:{id:2}}}", "data/*/(id)", scala.Array[java.lang.String]("1"))
    JsonMatcherTests.test("{data:{a:{id:1},b:{id:2}}}", "data/*/(id[])", scala.Array[java.lang.String]("[1,2]"))
    JsonMatcherTests.test("{items:[{x:1},{x:2},{x:3}]}", "items/*/(x[])", scala.Array[java.lang.String]("[1,2,3]"))
    JsonMatcherTests.test("{data:{x:1,y:2}}", "data/(x,y,x)", scala.Array[java.lang.String]("{x:1,y:2}"))
    JsonMatcherTests.test("{data:{x:1,y:2}}", "data/(x,y,z,a,b,c[])", scala.Array[java.lang.String]("{x:1,y:2}"))
    JsonMatcherTests.test("{data:{x:1,y:2}}", "**/**/**/data/**/**/**/(x,y,x)", scala.Array[java.lang.String]("{x:1,y:2}"))
    JsonMatcherTests.test((((((((((((("{\n" + "\titems: {\n") + "\t\tserver1: {\n") + "\t\t\tconfig: { // dead here\n") + "\t\t\t\thost: [ deadend ]\n") + "\t\t\t},\n") + "\t\t\tnested: {\n") + "\t\t\t\tconfig: {\n") + "\t\t\t\t\tport: 8080\n") + "\t\t\t\t}\n") + "\t\t\t}\n") + "\t\t}\n") + "\t}\n") + "}", "items/**/config/(port)", scala.Array[java.lang.String]("8080"))
    JsonMatcherTests.test("{data1:[{a:1},{b:2},{a:3},{b:4}],data2:[{a:5},{b:6},{a:7},{b:8}]}", "*/*/(a[],b[])", scala.Array[java.lang.String]("{a:[1,3,5,7],b:[2,4,6,8]}"))
  })
  test("wholeDocument")({
    JsonMatcherTests.test("{data:{items:[a,b,c]}}", "", scala.Array[java.lang.String]("{data:{items:[a,b,c]}}"))
    JsonMatcherTests.test("[a,b,{data:[1,2,3]},c]", "", scala.Array[java.lang.String]("[a,b,{data:[1,2,3]},c]"))
    JsonMatcherTests.test("string", "", scala.Array[java.lang.String]("string"))
    JsonMatcherTests.test("1234567", "", scala.Array[java.lang.String]("1234567"))
    JsonMatcherTests.test("1234.567", "", scala.Array[java.lang.String]("1234.567"))
    JsonMatcherTests.test("true", "", scala.Array[java.lang.String]("true"))
    JsonMatcherTests.test("false", "", scala.Array[java.lang.String]("false"))
    JsonMatcherTests.test("null", "", scala.Array[java.lang.String](null.asInstanceOf[java.lang.String]))
  })
  test("unescaping")({
    JsonMatcherTests.test("{data:\"He said \\\"hello\\\"\"}", "(data)", scala.Array[java.lang.String]("He said \"hello\""))
    JsonMatcherTests.test("{path:\"C:\\\\Users\\\\file.txt\"}", "(path)", scala.Array[java.lang.String]("C:\\Users\\file.txt"))
    JsonMatcherTests.test("{text:\"Line 1\\nLine 2\\nLine 3\"}", "(text)", scala.Array[java.lang.String]("Line 1\nLine 2\nLine 3"))
    JsonMatcherTests.test("{data:\"Column1\\tColumn2\\tColumn3\"}", "(data)", scala.Array[java.lang.String]("Column1\tColumn2\tColumn3"))
    JsonMatcherTests.test("{text:\"Windows\\r\\nLine ending\"}", "(text)", scala.Array[java.lang.String]("Windows\r\nLine ending"))
    JsonMatcherTests.test("{url:\"https:\\/\\/example.com\\/path\"}", "(url)", scala.Array[java.lang.String]("https://example.com/path"))
    JsonMatcherTests.test("{data:\"Before\\bAfter\"}", "(data)", scala.Array[java.lang.String]("BeforeAfter"))
    JsonMatcherTests.test("{data:\"Page1\\fPage2\"}", "(data)", scala.Array[java.lang.String]("Page1Page2"))
    JsonMatcherTests.test("{emoji:\"\\u2764\\uFE0F\"}", "(emoji)", scala.Array[java.lang.String]("❤️"))
    JsonMatcherTests.test("{text:\"\\u00A9 2024 Company\"}", "(text)", scala.Array[java.lang.String]("© 2024 Company"))
    JsonMatcherTests.test("{complex:\"Line 1\\n\\tIndented\\n\\\"Quoted\\\"\\nC:\\\\path\"}", "(complex)", scala.Array[java.lang.String]("Line 1\n\tIndented\n\"Quoted\"\nC:\\path"))
    JsonMatcherTests.test("{\"field\nwith\nnewlines\":\"value1\",\"field\twith\ttab\":\"value2\"}", "(field\nwith\nnewlines,field\twith\ttab)", scala.Array[java.lang.String]("{field\\nwith\\nnewlines:value1,field\\twith\\ttab:value2}"))
    JsonMatcherTests.test("{mixed:\"\\u0048ello\\nWorld\\t\\u0021\"}", "(mixed)", scala.Array[java.lang.String]("Hello\nWorld\t!"))
    JsonMatcherTests.test("{empty:\"\",\"escaped\":\"\n\t\"}", "(empty,escaped)", scala.Array[java.lang.String]("{empty:\"\",escaped:\\n\\t}"))
    JsonMatcherTests.test("{user:{\"name\":\"John \\\"Johnny\\\" Doe\",\"bio\":\"Line 1\\nLine 2\"}}", "user/(name,bio)", scala.Array[java.lang.String]("{name:John \"Johnny\" Doe,bio:Line 1\\nLine 2}"))
    JsonMatcherTests.test("{items:[\"\\\"quoted\\\"\",\"\ttabbed\",\"new\nline\"]}", "items/(*@)", scala.Array[java.lang.String]("\"quoted\"", "\ttabbed", "new\nline"))
    JsonMatcherTests.test("{emoji:\"\\uD83D\\uDE00\"}", "(emoji)", scala.Array[java.lang.String]("😀"))
    JsonMatcherTests.test("{all:\"\\\" \\\\ \\/ \\b \\f \\n \\r \\t\"}", "(all)", scala.Array[java.lang.String]("\" \\ /   \n \r \t"))
    JsonMatcherTests.test("{level1:{escaped:\"\\\"value\\\"\"},level2:[{item:\"\\nitem\\n\"}]}", "**/(escaped)", scala.Array[java.lang.String]("\"value\""))
    JsonMatcherTests.test("{text:\"\\u0041\\u0042\\u0043\"}", "(text)", scala.Array[java.lang.String]("ABC"))
    JsonMatcherTests.test("{text:\\u0041\\u0042\\u0043}", "(text)", scala.Array[java.lang.String]("ABC"))
    JsonMatcherTests.test("{names:[\"\\u4E2D\\u6587\",\"\\u65E5\\u672C\\u8A9E\"]}", "names/(*@)", scala.Array[java.lang.String]("中文", "日本語"))
    JsonMatcherTests.test(("{long:\"" + "\\n".repeat(10)) + "\"}", "(long)", scala.Array[java.lang.String]("\n".repeat(10)))
    JsonMatcherTests.test("{data:{\"field\\nname\":\"value\\there\"}}", "data/(*)", scala.Array[java.lang.String]("value\there"))
    JsonMatcherTests.test("{list:[{text:\"\\\"A\\\"\",value:1},{text:\"\\\"B\\\"\",value:2}]}", "list/*@/(text,value)", scala.Array[java.lang.String]("{text:\"\\\"A\\\"\",value:1}", "{text:\"\\\"B\\\"\",value:2}"))
    JsonMatcherTests.test(JsonMatcherTests.json, "**/*/(serial_num)", scala.Array[java.lang.String]("32131444"))
    JsonMatcherTests.test("{items:[{\"@\":special},{normal:value}]}", "items/*@/('@',normal)", scala.Array[java.lang.String]("{@:special}", "{normal:value}"))
    JsonMatcherTests.test("{da\\\\ta:{it'ems:[a,b,c]}}", "'da\\\\ta'/('it''ems')", scala.Array[java.lang.String]("[a,b,c]"))
    JsonMatcherTests.test("{*/()[\\\\]@',\\\\\\\\:{items:[a,b,c]}}", "'*/()[\\\\]@'',\\\\\\\\'/(items)", scala.Array[java.lang.String]("[a,b,c]"))
  })
  test("multiplePatterns")({
    JsonMatcherTests.test("{user:{name:John,age:30},meta:{version:1.0}}", scala.Array[java.lang.String]("user/(name)", "meta/(version)"), scala.Array[java.lang.String]("John", "1.0"))
    JsonMatcherTests.test("{user:{name:John},profile:{user:{name:Jane}}}", scala.Array[java.lang.String]("user/(name)", "profile/user/(name)"), scala.Array[java.lang.String]("John", "Jane"))
    JsonMatcherTests.test("{user:{name:John},profile:{user:{name:Jane}}}", scala.Array[java.lang.String]("user/(name)", "user/(name)", "profile/user/(name)", "profile/user/(name)"), scala.Array[java.lang.String]("John", "John", "Jane", "Jane"))
    JsonMatcherTests.test("{a:{b:{c:1}}}", scala.Array[java.lang.String]("**@/(b)", "**@/(c)"), scala.Array[java.lang.String]("1", "{c:1}"))
    JsonMatcherTests.test(JsonMatcherTests.json, scala.Array[java.lang.String]("*/(type)", "*/devices/*/(serial_num[])"), scala.Array[java.lang.String]("ENCHARGE", "[32131444,234234211,9834711]"))
  })
  test("keys")({
    JsonMatcherTests.test("{a:1,b:2,c:3}", "()[]", scala.Array[java.lang.String]("[a,b,c]"))
    JsonMatcherTests.test("{a:1,b:2,c:3}", "()[],(b)", scala.Array[java.lang.String]("{\"\":[a,b,c]}"))
    JsonMatcherTests.test("{a:1,b:2,c:3}", "()[],(b)", scala.Array[java.lang.String]("{\"\":[a,b,c]}"))
    JsonMatcherTests.test("{object:{a:1,b:2,c:3}}", "object/()[]", scala.Array[java.lang.String]("[a,b,c]"))
    JsonMatcherTests.test("{a:1,b:2,c:3}", "()", scala.Array[java.lang.String]("a"))
    JsonMatcherTests.test("{object:{a:1,b:2,c:3}}", "object/()", scala.Array[java.lang.String]("a"))
    JsonMatcherTests.test("[a,b,c]", "()[]", scala.Array[java.lang.String]())
    JsonMatcherTests.test("{object:{a:1,b:2,c:3}}", "*/()[]", scala.Array[java.lang.String]("[a,b,c]"))
    JsonMatcherTests.test("{object:{a:1,b:2,c:{d:{e:3},f:[1,2,3]}}}", "**/()[]", scala.Array[java.lang.String]("[object,a,b,c,d,e,f]"))
  })
  test("earlyEnd")({
    JsonMatcherTests.test("extra", "{first:{id:1},second:{data:ignored},extra:should-not-parse}", scala.Array[java.lang.String]("first/(id@)", "(second)"), scala.Array[java.lang.String]("1", "{data:ignored}"))
    JsonMatcherTests.test("extra", "{first:{id:1},second:{id:ignored},extra:should-not-parse}", scala.Array[java.lang.String]("*/(id@)", "*/(id)"), scala.Array[java.lang.String]("1", "1"))
    JsonMatcherTests.test("extra", "{first:{id:1},second:{id:ignored},extra:should-not-parse}", scala.Array[java.lang.String]("(first,second)@"), scala.Array[java.lang.String]("{id:1}", "{id:ignored}"))
    JsonMatcherTests.test("extra", "{first:{id:1},second:{id:ignored},extra:should-not-parse}", scala.Array[java.lang.String]("second/(id@)"), scala.Array[java.lang.String]("ignored"))
    JsonMatcherTests.test("extra", "{first:{id:1},second:{id:ignored},third:{other:1},extra:should-not-parse}", scala.Array[java.lang.String]("(first,second)@", "*/(other)"), scala.Array[java.lang.String]("{id:1}", "{id:ignored}", "1"))
    JsonMatcherTests.test("extra", "{value:1,nested:{value:2},extra:{value:3}}", scala.Array[java.lang.String]("(value@),*/(value@)"), scala.Array[java.lang.String]("1", "2"))
  })
  test("rejection")({
    {
      val values: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue]]
      val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
      matcher.addPattern("*/(type@)", (value: com.badlogic.gdx.utils.JsonValue) => {
        if (value.equalsString("ENCHARGE")) {
          matcher.rejectAll()
        } else ()
      })
      matcher.addPattern("*/devices/*@/(serial_num,percentFull)", (value: com.badlogic.gdx.utils.JsonValue) => JsonMatcherTests.copy(value, values))
      matcher.parse(JsonMatcherTests.json)
      JsonMatcherTests.assertValueCount(1, values)
      val value: com.badlogic.gdx.utils.JsonValue = values.first()
      assertEquals(value.getString("serial_num"), "9834711")
      assertEquals(value.get("percentFull"), null)
    }
    JsonMatcherTests.rejectAll(JsonMatcherTests.json, "(type@)", "**/*/*@/(serial_num)", scala.Array[java.lang.String]())
    JsonMatcherTests.rejectAll(JsonMatcherTests.json, "**/(serial_num@)", "**/*@/(serial_num[])", scala.Array[java.lang.String]())
    JsonMatcherTests.rejectAll(JsonMatcherTests.json, "**/*/(serial_num@)", "**/*@/(serial_num[])", scala.Array[java.lang.String]())
    JsonMatcherTests.rejectAll(JsonMatcherTests.json, "**/*/**/(serial_num@)", "**/*@/(serial_num[])", scala.Array[java.lang.String]())
    JsonMatcherTests.rejectAll(JsonMatcherTests.json, "**/*/(serial_num@)", "**/(serial_num[])@", scala.Array[java.lang.String]())
    JsonMatcherTests.rejectAll(JsonMatcherTests.json, "**/*/(serial_num@)", "**/*/*@/(serial_num)", scala.Array[java.lang.String]())
    JsonMatcherTests.rejectAll(JsonMatcherTests.json, "**/*/(part_num@)", "**/*/*@/(serial_num)", scala.Array[java.lang.String]())
    JsonMatcherTests.rejectAll(JsonMatcherTests.json, "**/*/(object@)", "**/*@/*/(serial_num)", scala.Array[java.lang.String]())
    JsonMatcherTests.rejectAll("{a:{x:{reject:true}},b:{x:{value:found}}}", "**/x/(reject@)", "**/x/(value@)", scala.Array[java.lang.String]("found"))
    JsonMatcherTests.rejectAll("{items:[{bad:{reject:true}},{good:{value:1}}]}", "items/*@/bad/(reject)", "items/*@/good/(value)", scala.Array[java.lang.String]("1"))
    JsonMatcherTests.rejectAll("{items:[{id:1},{id:2,skip:true},{id:3}]}", "items/*/(skip@)", "items/*@/(id,skip)", scala.Array[java.lang.String]("{id:1}", "{id:3}"))
    JsonMatcherTests.rejectAll("{a:{b:{reject:here,c:{d:{value:no}}},e:{c:{d:{value:yes}}}}}", "**/b/(reject@)", "**/c/**/d/(value)", scala.Array[java.lang.String]("yes"))
    JsonMatcherTests.rejectAll("{data:{type:[bad],info:important}}", "data/type@/(*)", "data@/(info)", scala.Array[java.lang.String]("important"))
    JsonMatcherTests.rejectAll("{a:{b:{target:{x:1}},c:{target:{x:2,reject:true}}},d:{target:{x:3}}}", "**/(reject@)", "**/(x[])", scala.Array[java.lang.String]("[3]"))
  })
  test("explicitEnd")({
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern("*/(type@)", (value: com.badlogic.gdx.utils.JsonValue) => {
      if (value.equalsString("ENCHARGE")) {
        matcher.`end`()
      } else ()
    })
    val values: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue]]
    matcher.addPattern("*/devices/*@/(serial_num,percentFull)", (value: com.badlogic.gdx.utils.JsonValue) => JsonMatcherTests.copy(value, values))
    matcher.parse(JsonMatcherTests.json)
    JsonMatcherTests.assertValueCount(0, values)
  })
  test("explicitStop")({
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern("*/(type@)", (value: com.badlogic.gdx.utils.JsonValue) => {
      if (value.equalsString("ENCHARGE")) {
        matcher.stop()
      } else ()
    })
    val values: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue]]
    matcher.addPattern("*/devices/*@/(serial_num,percentFull)", (value: com.badlogic.gdx.utils.JsonValue) => JsonMatcherTests.copy(value, values))
    matcher.parse(JsonMatcherTests.json)
    JsonMatcherTests.assertValueCount(0, values)
  })
  test("parseValue")({
    var root: com.badlogic.gdx.utils.JsonValue = new com.badlogic.gdx.utils.JsonMatcher().parseValue(JsonMatcherTests.json)
    assert(root.child$field.hasChild("devices"))
    root = new com.badlogic.gdx.utils.JsonMatcher(scala.Array[java.lang.String]("")).parseValue(JsonMatcherTests.json)
    assert(root.child$field.hasChild("devices"))
    root = new com.badlogic.gdx.utils.JsonMatcher(scala.Array[java.lang.String]("*/devices/(*)")).parseValue(JsonMatcherTests.json)
    assertEquals(root.getInt("percentFull"), 100)
    val values: com.badlogic.gdx.utils.JsonValue = new com.badlogic.gdx.utils.JsonMatcher(scala.Array[java.lang.String]("*/(devices)", "*/devices/(*)")).parseValue(JsonMatcherTests.json)
    assertEquals(values.child$field.name$field, "devices")
    assertEquals(values.child$field.next$field.getInt("percentFull"), 100)
  })
  test("paths")({
    val paths: com.badlogic.gdx.utils.Array[?] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[?]]
    val parents: com.badlogic.gdx.utils.Array[?] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[?]]
    val parents2: com.badlogic.gdx.utils.Array[?] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[?]];
    {
      val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
      matcher.setProcessor((value: com.badlogic.gdx.utils.JsonValue) => {
        paths.asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.Object]].add(matcher.path().asInstanceOf[java.lang.Object])
        parents.asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.Object]].add(matcher.parent().asInstanceOf[java.lang.Object])
        parents2.asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.Object]].add(matcher.parent(2).asInstanceOf[java.lang.Object])
      })
      matcher.addPattern("*/devices/*@/(serial_num,percentFull)")
      matcher.parse(JsonMatcherTests.json)
    };
    {
      val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
      matcher.addPattern("**@/(value)", (value: com.badlogic.gdx.utils.JsonValue) => {
        paths.asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.Object]].add(matcher.path().asInstanceOf[java.lang.Object])
        parents.asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.Object]].add(matcher.parent().asInstanceOf[java.lang.Object])
        parents2.asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.Object]].add(matcher.parent(2).asInstanceOf[java.lang.Object])
      })
      matcher.parse(JsonMatcherTests.json)
      matcher.parse("{a:{b:{c:{d:{e:{f:{value:deep}}}}}}}")
    }
    JsonMatcherTests.assertValueCount(6, paths.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue]])
    assertEquals(paths.first().asInstanceOf[java.lang.Object], "[]/{}/devices/{}")
    assertEquals(paths.first().asInstanceOf[java.lang.Object], "[]/{}/devices/{}")
    assertEquals(paths.get(1).asInstanceOf[java.lang.Object], "[]/{}/devices/{}")
    assertEquals(paths.get(2).asInstanceOf[java.lang.Object], "[]/{}/devices/{}")
    assertEquals(paths.get(3).asInstanceOf[java.lang.Object], "[]/{}/devices/{}/child")
    assertEquals(paths.get(4).asInstanceOf[java.lang.Object], "[]/{}/devices/{}/child")
    assertEquals(paths.get(5).asInstanceOf[java.lang.Object], "{}/a/b/c/d/e/f")
    JsonMatcherTests.assertValueCount(6, parents.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue]])
    assertEquals(parents.first().asInstanceOf[java.lang.Object], "{}")
    assertEquals(parents.get(1).asInstanceOf[java.lang.Object], "{}")
    assertEquals(parents.get(2).asInstanceOf[java.lang.Object], "{}")
    assertEquals(parents.get(3).asInstanceOf[java.lang.Object], "child")
    assertEquals(parents.get(4).asInstanceOf[java.lang.Object], "child")
    assertEquals(parents.get(5).asInstanceOf[java.lang.Object], "f")
    JsonMatcherTests.assertValueCount(6, parents2.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue]])
    assertEquals(parents2.first().asInstanceOf[java.lang.Object], "{}")
    assertEquals(parents2.get(1).asInstanceOf[java.lang.Object], "{}")
    assertEquals(parents2.get(2).asInstanceOf[java.lang.Object], "{}")
    assertEquals(parents2.get(3).asInstanceOf[java.lang.Object], "devices")
    assertEquals(parents2.get(4).asInstanceOf[java.lang.Object], "devices")
    assertEquals(parents2.get(5).asInstanceOf[java.lang.Object], "d")
  })
  test("dataTypes")({
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    val values: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue]]
    matcher.addPattern("*/devices/*/(maxCellTemp,temperature,dc_switch_off,admin_state_str,sleep_enabled,device_status,object)")
    matcher.setProcessor((value: com.badlogic.gdx.utils.JsonValue) => JsonMatcherTests.copy(value, values))
    matcher.parse(JsonMatcherTests.json)
    JsonMatcherTests.assertValueCount(1, values)
    val value: com.badlogic.gdx.utils.JsonValue = values.first()
    value.getLong("maxCellTemp")
    value.getDouble("temperature")
    assert(value.has("dc_switch_off"))
    assert(value.get("dc_switch_off").isNull())
    value.getString("admin_state_str")
    value.getBoolean("sleep_enabled")
    value.get("device_status").asStringArray()
    assert(value.get("object").`type`() == com.badlogic.gdx.utils.JsonValue.ValueType.`object`)
  })
  test("filtering")({
    {
      val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
      val values: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue]]
      val enpower: scala.Int = matcher.addPattern("*/devices/*@/(serial_num)", (value: com.badlogic.gdx.utils.JsonValue) => JsonMatcherTests.copy(value, values))
      val encharge: scala.Int = matcher.addPattern("*/devices/*@/(serial_num,percentFull)", (value: com.badlogic.gdx.utils.JsonValue) => JsonMatcherTests.copy(value, values))
      matcher.addPattern("*/(type@)", (value: com.badlogic.gdx.utils.JsonValue) => {
        if (value.equalsString("ENPOWER")) {
          matcher.reject(encharge)
        } else {
          if (value.equalsString("ENCHARGE")) {
            matcher.reject(enpower)
          } else {
            fail("Unexpected type: " + value)
          }
        }
      })
      matcher.parse(JsonMatcherTests.json)
      JsonMatcherTests.assertValueCount(3, values)
      assertEquals(JsonMatcherTests.toString(values), "{serial_num:32131444,percentFull:100}, {percentFull:75,serial_num:234234211}, 9834711")
    };
    {
      val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
      val values: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue]]
      val `type`: scala.Array[java.lang.String] = scala.Array[java.lang.String]("")
      matcher.addPattern("*/(type@)", (value: com.badlogic.gdx.utils.JsonValue) => {
        `type`(0) = value.asString()
        `type`(0)
      })
      matcher.addPattern("*/devices/*@/(serial_num)", (value: com.badlogic.gdx.utils.JsonValue) => {
        if (`type`(0).equals("ENPOWER")) {
          JsonMatcherTests.copy(value, values)
        } else {
          matcher.reject()
        }
      })
      matcher.addPattern("*/devices/*@/(serial_num,percentFull)", (value: com.badlogic.gdx.utils.JsonValue) => {
        if (`type`(0).equals("ENCHARGE")) {
          JsonMatcherTests.copy(value, values)
        } else {
          matcher.reject()
        }
      })
      matcher.parse(JsonMatcherTests.json)
      JsonMatcherTests.assertValueCount(3, values)
      assertEquals(JsonMatcherTests.toString(values), "{serial_num:32131444,percentFull:100}, {percentFull:75,serial_num:234234211}, 9834711")
    };
    {
      val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
      val values: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue]]
      matcher.addPattern("*/(type@),devices/*@/(serial_num)", (value: com.badlogic.gdx.utils.JsonValue) => {
        if (value.nameEquals("type")) {
          if (!value.equalsString("ENPOWER")) {
            matcher.reject()
          } else ()
        } else {
          JsonMatcherTests.copy(value, values)
        }
      })
      matcher.addPattern("*/(type@),devices/*@/(serial_num,percentFull)", (value: com.badlogic.gdx.utils.JsonValue) => {
        if (value.nameEquals("type")) {
          if (!value.equalsString("ENCHARGE")) {
            matcher.reject()
          } else ()
        } else {
          JsonMatcherTests.copy(value, values)
        }
      })
      matcher.parse(JsonMatcherTests.json)
      JsonMatcherTests.assertValueCount(3, values)
      assertEquals(JsonMatcherTests.toString(values), "{serial_num:32131444,percentFull:100}, {percentFull:75,serial_num:234234211}, 9834711")
    }
  })
  @org.junit.Test(expected = classOf[java.lang.IllegalArgumentException])
  def invalidPattern1(): scala.Unit = {
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern("path/(to),nowhere,/")
  }
  @org.junit.Test(expected = classOf[java.lang.IllegalArgumentException])
  def invalidPattern2(): scala.Unit = {
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern("(other),path()")
  }
  @org.junit.Test(expected = classOf[java.lang.IllegalArgumentException])
  def invalidPattern3(): scala.Unit = {
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern("(other),path(name")
  }
  @org.junit.Test(expected = classOf[java.lang.IllegalArgumentException])
  def invalidPattern4(): scala.Unit = {
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern("path/name")
  }
  @org.junit.Test(expected = classOf[java.lang.IllegalArgumentException])
  def invalidPattern5(): scala.Unit = {
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern("a//b/c/(value)")
  }
  @org.junit.Test(expected = classOf[java.lang.IllegalArgumentException])
  def invalidPattern6(): scala.Unit = {
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern("a//b/(c)")
  }
  @org.junit.Test(expected = classOf[java.lang.IllegalArgumentException])
  def invalidPattern7(): scala.Unit = {
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern("a/**,b/(c)")
  }
  @org.junit.Test(expected = classOf[java.lang.IllegalArgumentException])
  def invalidPattern8(): scala.Unit = {
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern("a/[]b/(c)")
  }
  @org.junit.Test(expected = classOf[java.lang.IllegalArgumentException])
  def invalidPattern9(): scala.Unit = {
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern("a/@b/(c)")
  }
  @org.junit.Test(expected = classOf[java.lang.IllegalArgumentException])
  def invalidPattern10(): scala.Unit = {
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern("a/@b/(c)")
  }
  @org.junit.Test(expected = classOf[java.lang.IllegalArgumentException])
  def invalidPattern11(): scala.Unit = {
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern("a/(b/c)")
  }
  @org.junit.Test(expected = classOf[java.lang.IllegalArgumentException])
  def invalidPattern12(): scala.Unit = {
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern("/b/(c)")
  }
  @org.junit.Test(expected = classOf[java.lang.IllegalArgumentException])
  def invalidPattern13(): scala.Unit = {
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern("a/,/(b)")
  }
  @org.junit.Test(expected = classOf[java.lang.IllegalArgumentException])
  def invalidPattern14(): scala.Unit = {
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern("a/b[]/(c)")
  }
  @org.junit.Test(expected = classOf[java.lang.IllegalStateException])
  def invalidPattern15(): scala.Unit = {
    new com.badlogic.gdx.utils.JsonMatcher(scala.Array[java.lang.String]("a/b/(c@)")).parseValue("{}")
  }
}
object JsonMatcherTests {
  private final val json: java.lang.String = (((((((((((((((((((((((((((((((((((((((((((((((((("[{\n" + "type: ENCHARGE,\n") + "devices: [\n") + "\t{\n") + "\t\tpart_num: 830-00703-r84,\n") + "\t\tserial_num: \"32131444\",\n") + "\t\tinstalled: 17519017,\n") + "\t\tdevice_status: [\n") + "\t\t\tenvoy.global.ok,\n") + "\t\t\tprop.done\n") + "\t\t],\n") + "\t\tlast_rpt_date: 1753239176,\n") + "\t\tadmin_state: 6,\n") + "\t\tadmin_state_str: ENCHG_STATE_READY,\n") + "\t\tcreated_date: 1751974017,\n") + "\t\timg_load_date: 1751974017,\n") + "\t\timg_pnum_running: \"2.0.8116_rel/22.33\",\n") + "\t\tbmu_fw_version: 2.1.38,\n") + "\t\tcommunicating: true,\n") + "\t\tsleep_enabled: false,\n") + "\t\tpercentFull: 100,\n") + "\t\ttemperature: 31.4,\n") + "\t\tmaxCellTemp: 31,\n") + "\t\treported_enc_grid_state: grid-tied,\n") + "\t\tcomm_level_sub_ghz: 5,\n") + "\t\tcomm_level_2_4_ghz: 5,\n") + "\t\tled_status: 14,\n") + "\t\tdc_switch_off: null,\n") + "\t\tchild: { value: 1 },\n") + "\t\tencharge_rev: 1,\n") + "\t\tencharge_capacity: 3360,\n") + "\t\tphase: ph-a,\n") + "\t\tder_index: 1,\n") + "\t\tobject: {}\n") + "\t},\n") + "\t{\n") + "\t\tpart_num: 830-00703-r84,\n") + "\t\tinstalled: 17518704,\n") + "\t\tpercentFull: 75,\n") + "\t\tserial_num: \"234234211\",\n") + "\t\tchild: { value: 2 },\n") + "\t\tdevice_status: [\n") + "\t\t\tenvoy.global.failure,\n") + "\t\t\tprop.waiting\n") + "\t\t]\n") + "\t}\n") + "]},{\n") + "type: ENPOWER,\n") + "devices: [{\n") + "\tserial_num: \"9834711\",\n") + "}]\n") + "}]"
  def rejectAll(json: java.lang.String, rejectPattern: java.lang.String, pattern: java.lang.String, expected: scala.Array[java.lang.String]): scala.Unit = {
    val values: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue]]
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher()
    matcher.addPattern(rejectPattern, (value: com.badlogic.gdx.utils.JsonValue) => {
      matcher.rejectAll()
      matcher.clearAll()
    })
    matcher.addPattern(pattern, (value: com.badlogic.gdx.utils.JsonValue) => JsonMatcherTests.copy(value, values))
    matcher.parse(json)
    try {
      JsonMatcherTests.assertValueCount(expected.length, values);
      { var i: scala.Int = 0; val n: scala.Int = expected.length; while (i < n) { {
        assertEquals(values.get(i).toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.minimal), expected(i), "Pattern " + i)
      }; i = i + 1 } }
    } catch {
      case ex: java.lang.AssertionError => {
        JsonMatcherTests.printResults(matcher, values, json, scala.Array[java.lang.String](rejectPattern, pattern), expected)
        throw ex
      }
    }
  }
  def test(json: java.lang.String, pattern: java.lang.String, expected: scala.Array[java.lang.String]): scala.Unit = {
    JsonMatcherTests.test(null, json, scala.Array[java.lang.String](pattern), expected)
  }
  def test(json: java.lang.String, patterns: scala.Array[java.lang.String], expected: scala.Array[java.lang.String]): scala.Unit = {
    JsonMatcherTests.test(null, json, patterns, expected)
  }
  def test(notParsedValue: java.lang.String, json: java.lang.String, patterns: scala.Array[java.lang.String], expected: scala.Array[java.lang.String]): scala.Unit = {
    val values: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue]]
    val ended: scala.Array[scala.Boolean] = new scala.Array[scala.Boolean](1)
    val matcher: com.badlogic.gdx.utils.JsonMatcher = new com.badlogic.gdx.utils.JsonMatcher() {
      @java.lang.Override
      override def value(name: com.badlogic.gdx.utils.JsonSkimmer.JsonToken, value: com.badlogic.gdx.utils.JsonSkimmer.JsonToken): scala.Unit = {
        if ((notParsedValue != null) && name.equals(notParsedValue)) {
          fail("Should have ended before parsing value: " + notParsedValue)
        } else ()
        super.value(name, value)
      }
      @java.lang.Override
      override def `end`(): scala.Unit = {
        super.`end`()
        ended(0) = true
      }
    }
    matcher.setProcessor((value: com.badlogic.gdx.utils.JsonValue) => JsonMatcherTests.copy(value, values))
    for (pattern <- patterns) {
      matcher.addPattern(pattern)
    }
    matcher.parse(json)
    try {
      JsonMatcherTests.assertValueCount(expected.length, values);
      { var i: scala.Int = 0; val n: scala.Int = expected.length; while (i < n) { {
        val value: com.badlogic.gdx.utils.JsonValue = values.get(i)
        assertEquals(value.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.minimal), expected(i), "Pattern " + i)
      }; i = i + 1 } }
      if ((notParsedValue != null) && (!ended(0))) {
        fail("Should have ended but did not")
      } else ()
    } catch {
      case ex: java.lang.AssertionError => {
        JsonMatcherTests.printResults(matcher, values, json, patterns, expected)
        throw ex
      }
    }
  }
  def printResults(matcher: com.badlogic.gdx.utils.JsonMatcher, values: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue], json: java.lang.String, patterns: scala.Array[java.lang.String], expected: scala.Array[java.lang.String]): scala.Unit = {
    java.lang.System.out.println(" JSON: " + json)
    if (patterns.length == 1) {
      java.lang.System.out.println(" Pattern: " + patterns(0))
    } else {
      java.lang.System.out.println("Patterns: " + java.util.Arrays.toString(patterns.asInstanceOf[scala.Array[java.lang.Object]]))
    }
    java.lang.System.out.println("  Parsed: " + JsonMatcherTests.toString(matcher, patterns))
    java.lang.System.out.println((("Expected: " + expected.length) + " ") + java.util.Arrays.toString(expected.asInstanceOf[scala.Array[java.lang.Object]]).replace("\n", "\\n").replace("\t", "\\t").replaceAll("^\\[|\\]$", ""))
    java.lang.System.out.println((("  Actual: " + values.size) + " ") + JsonMatcherTests.toString(values))
  }
  def assertValueCount(count: scala.Int, values: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue]): scala.Unit = {
    if (values.size != count) {
      java.lang.System.out.println((("Actual: " + values.size) + " ") + JsonMatcherTests.toString(values))
    } else ()
    assertEquals(values.size, count, "Wrong match count")
  }
  def toString(matcher: com.badlogic.gdx.utils.JsonMatcher, patterns: scala.Array[java.lang.String]): java.lang.String = {
    val buffer: com.badlogic.gdx.utils.CharArray = new com.badlogic.gdx.utils.CharArray()
    for (pattern <- patterns) {
      if (pattern.isEmpty()) {
        buffer.append("\"\"", ", ")
      } else {
        buffer.append(com.badlogic.gdx.utils.PatternParser.parse(matcher, pattern, null).toString(), ", ")
      }
    }
    return buffer.replaceAll("\n", "\\n").replaceAll("\t", "\\t").toString()
  }
  def toString(values: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue]): java.lang.String = {
    val buffer: com.badlogic.gdx.utils.CharArray = new com.badlogic.gdx.utils.CharArray()
    for (value <- values) {
      buffer.append(value.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.minimal), ", ")
    }
    return buffer.replaceAll("\n", "\\n").replaceAll("\t", "\\t").toString()
  }
  def copy(value: com.badlogic.gdx.utils.JsonValue, values: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue]): scala.Unit = {
    values.add(new com.badlogic.gdx.utils.JsonValue(value))
  }
}