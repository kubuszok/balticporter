package com.badlogic.gdx.files

class FileHandle {
  var file$field: java.io.File = null.asInstanceOf[java.io.File]
  var type$field: com.badlogic.gdx.Files.FileType = null.asInstanceOf[com.badlogic.gdx.Files.FileType]
  def this(fileName: java.lang.String) = {
    this()
    this.file$field = new java.io.File(fileName)
    this.type$field = com.badlogic.gdx.Files.FileType.Absolute
  }
  def this(file: java.io.File) = {
    this()
    this.file$field = file
    this.type$field = com.badlogic.gdx.Files.FileType.Absolute
  }
  def this(fileName: java.lang.String, `type`: com.badlogic.gdx.Files.FileType) = {
    this()
    this.type$field = `type`
    this.file$field = new java.io.File(fileName)
  }
  def this(file: java.io.File, `type`: com.badlogic.gdx.Files.FileType) = {
    this()
    this.file$field = file
    this.type$field = `type`
  }
  def path(): java.lang.String = {
    return this.file$field.getPath().replace('\\', '/')
  }
  def name(): java.lang.String = {
    return this.file$field.getName()
  }
  def `extension`(): java.lang.String = {
    val name: java.lang.String = this.file$field.getName()
    val dotIndex: scala.Int = name.lastIndexOf('.')
    if (dotIndex == (-1)) {
      return ""
    } else ()
    return name.substring(dotIndex + 1)
  }
  def nameWithoutExtension(): java.lang.String = {
    val name: java.lang.String = this.file$field.getName()
    val dotIndex: scala.Int = name.lastIndexOf('.')
    if (dotIndex == (-1)) {
      return name
    } else ()
    return name.substring(0, dotIndex)
  }
  def pathWithoutExtension(): java.lang.String = {
    val path: java.lang.String = this.file$field.getPath().replace('\\', '/')
    val dotIndex: scala.Int = path.lastIndexOf('.')
    if (dotIndex == (-1)) {
      return path
    } else ()
    return path.substring(0, dotIndex)
  }
  def `type`(): com.badlogic.gdx.Files.FileType = {
    return this.type$field
  }
  def file(): java.io.File = {
    if (this.type$field == com.badlogic.gdx.Files.FileType.External) {
      return new java.io.File(com.badlogic.gdx.Gdx.files.getExternalStoragePath(), this.file$field.getPath())
    } else ()
    return this.file$field
  }
  def read(): java.io.InputStream = {
    if (((this.type$field == com.badlogic.gdx.Files.FileType.Classpath) || ((this.type$field == com.badlogic.gdx.Files.FileType.Internal) && (!this.file().exists()))) || ((this.type$field == com.badlogic.gdx.Files.FileType.Local) && (!this.file().exists()))) {
      val input: java.io.InputStream = classOf[FileHandle].getResourceAsStream("/" + this.file$field.getPath().replace('\\', '/'))
      if (input == null) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(((("File not found: " + this.file$field) + " (") + this.type$field) + ")")
      } else ()
      return input
    } else ()
    try {
      return new java.io.FileInputStream(this.file())
    } catch {
      case ex: java.lang.Exception => {
        if (this.file().isDirectory()) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException(((("Cannot open a stream to a directory: " + this.file$field) + " (") + this.type$field) + ")", ex)
        } else ()
        throw new com.badlogic.gdx.utils.GdxRuntimeException(((("Error reading file: " + this.file$field) + " (") + this.type$field) + ")", ex)
      }
    }
  }
  def read(bufferSize: scala.Int): java.io.BufferedInputStream = {
    return new java.io.BufferedInputStream(this.read(), bufferSize)
  }
  def reader(): java.io.Reader = {
    return new java.io.InputStreamReader(this.read())
  }
  def reader(charset: java.lang.String): java.io.Reader = {
    val stream: java.io.InputStream = this.read()
    try {
      return new java.io.InputStreamReader(stream, charset)
    } catch {
      case ex: java.io.UnsupportedEncodingException => {
        com.badlogic.gdx.utils.StreamUtils.closeQuietly(stream)
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Error reading file: " + this, ex)
      }
    }
  }
  def reader(bufferSize: scala.Int): java.io.BufferedReader = {
    return new java.io.BufferedReader(new java.io.InputStreamReader(this.read()), bufferSize)
  }
  def reader(bufferSize: scala.Int, charset: java.lang.String): java.io.BufferedReader = {
    try {
      return new java.io.BufferedReader(new java.io.InputStreamReader(this.read(), charset), bufferSize)
    } catch {
      case ex: java.io.UnsupportedEncodingException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Error reading file: " + this, ex)
      }
    }
  }
  def readString(): java.lang.String = {
    return this.readString(null)
  }
  def readString(charset: java.lang.String): java.lang.String = {
    val output: java.lang.StringBuilder = new java.lang.StringBuilder(this.estimateLength())
    var reader: java.io.InputStreamReader = null
    try {
      if (charset == null) {
        reader = new java.io.InputStreamReader(this.read())
      } else {
        reader = new java.io.InputStreamReader(this.read(), charset)
      }
      val buffer: scala.Array[scala.Char] = new scala.Array[scala.Char](256)
      while (true) {
        val length: scala.Int = reader.read(buffer)
        if (length == (-1)) {
          /* break */ ()
        } else ()
        output.append(buffer, 0, length)
      }
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Error reading layout file: " + this, ex)
      }
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(reader)
    }
    return output.toString()
  }
  def readBytes(): scala.Array[scala.Byte] = {
    val input: java.io.InputStream = this.read()
    try {
      return com.badlogic.gdx.utils.StreamUtils.copyStreamToByteArray(input, this.estimateLength())
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Error reading file: " + this, ex)
      }
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(input)
    }
  }
  private def estimateLength(): scala.Int = {
    val length: scala.Int = this.length().asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    return if (length != 0) length else 512
  }
  def readBytes(bytes: scala.Array[scala.Byte], offset: scala.Int, size: scala.Int): scala.Int = {
    val input: java.io.InputStream = this.read()
    var position: scala.Int = 0
    try {
      while (true) {
        val count: scala.Int = input.read(bytes, offset + position, size - position)
        if (count <= 0) {
          /* break */ ()
        } else ()
        position = position + count
      }
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Error reading file: " + this, ex)
      }
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(input)
    }
    return position - offset
  }
  def map(): java.nio.ByteBuffer = {
    return this.map(java.nio.channels.FileChannel.MapMode.READ_ONLY)
  }
  def map(mode: java.nio.channels.FileChannel#MapMode): java.nio.ByteBuffer = {
    if (this.type$field == com.badlogic.gdx.Files.FileType.Classpath) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot map a classpath file: " + this)
    } else ()
    var raf: java.io.RandomAccessFile = null
    try {
      val f: java.io.File = this.file()
      raf = new java.io.RandomAccessFile(f, if (mode == java.nio.channels.FileChannel.MapMode.READ_ONLY) "r" else "rw")
      val fileChannel: java.nio.channels.FileChannel = raf.getChannel()
      val map: java.nio.ByteBuffer = fileChannel.map(mode, 0, f.length())
      map.order(java.nio.ByteOrder.nativeOrder())
      return map
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(((("Error memory mapping file: " + this) + " (") + this.type$field) + ")", ex)
      }
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(raf)
    }
  }
  def write(append: scala.Boolean): java.io.OutputStream = {
    if (this.type$field == com.badlogic.gdx.Files.FileType.Classpath) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot write to a classpath file: " + this.file$field)
    } else ()
    if (this.type$field == com.badlogic.gdx.Files.FileType.Internal) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot write to an internal file: " + this.file$field)
    } else ()
    this.parent().mkdirs()
    try {
      return new java.io.FileOutputStream(this.file(), append)
    } catch {
      case ex: java.lang.Exception => {
        if (this.file().isDirectory()) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException(((("Cannot open a stream to a directory: " + this.file$field) + " (") + this.type$field) + ")", ex)
        } else ()
        throw new com.badlogic.gdx.utils.GdxRuntimeException(((("Error writing file: " + this.file$field) + " (") + this.type$field) + ")", ex)
      }
    }
  }
  def write(append: scala.Boolean, bufferSize: scala.Int): java.io.OutputStream = {
    return new java.io.BufferedOutputStream(this.write(append), bufferSize)
  }
  def write(input: java.io.InputStream, append: scala.Boolean): scala.Unit = {
    var output: java.io.OutputStream = null
    try {
      output = this.write(append)
      com.badlogic.gdx.utils.StreamUtils.copyStream(input, output)
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(((("Error stream writing to file: " + this.file$field) + " (") + this.type$field) + ")", ex)
      }
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(input)
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(output)
    }
  }
  def writer(append: scala.Boolean): java.io.Writer = {
    return this.writer(append, null)
  }
  def writer(append: scala.Boolean, charset: java.lang.String): java.io.Writer = {
    if (this.type$field == com.badlogic.gdx.Files.FileType.Classpath) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot write to a classpath file: " + this.file$field)
    } else ()
    if (this.type$field == com.badlogic.gdx.Files.FileType.Internal) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot write to an internal file: " + this.file$field)
    } else ()
    this.parent().mkdirs()
    try {
      val output: java.io.FileOutputStream = new java.io.FileOutputStream(this.file(), append)
      if (charset == null) {
        return new java.io.OutputStreamWriter(output)
      } else {
        return new java.io.OutputStreamWriter(output, charset)
      }
    } catch {
      case ex: java.io.IOException => {
        if (this.file().isDirectory()) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException(((("Cannot open a stream to a directory: " + this.file$field) + " (") + this.type$field) + ")", ex)
        } else ()
        throw new com.badlogic.gdx.utils.GdxRuntimeException(((("Error writing file: " + this.file$field) + " (") + this.type$field) + ")", ex)
      }
    }
  }
  def writeString(string: java.lang.String, append: scala.Boolean): scala.Unit = {
    this.writeString(string, append, null)
  }
  def writeString(string: java.lang.String, append: scala.Boolean, charset: java.lang.String): scala.Unit = {
    var writer: java.io.Writer = null
    try {
      writer = this.writer(append, charset)
      writer.write(string)
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(((("Error writing file: " + this.file$field) + " (") + this.type$field) + ")", ex)
      }
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(writer)
    }
  }
  def writeBytes(bytes: scala.Array[scala.Byte], append: scala.Boolean): scala.Unit = {
    val output: java.io.OutputStream = this.write(append)
    try {
      output.write(bytes)
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(((("Error writing file: " + this.file$field) + " (") + this.type$field) + ")", ex)
      }
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(output)
    }
  }
  def writeBytes(bytes: scala.Array[scala.Byte], offset: scala.Int, length: scala.Int, append: scala.Boolean): scala.Unit = {
    val output: java.io.OutputStream = this.write(append)
    try {
      output.write(bytes, offset, length)
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(((("Error writing file: " + this.file$field) + " (") + this.type$field) + ")", ex)
      }
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(output)
    }
  }
  def list(): scala.Array[FileHandle] = {
    if (this.type$field == com.badlogic.gdx.Files.FileType.Classpath) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot list a classpath directory: " + this.file$field)
    } else ()
    val relativePaths: scala.Array[java.lang.String] = this.file().list()
    if (relativePaths == null) {
      return new scala.Array[FileHandle](0)
    } else ()
    val handles: scala.Array[FileHandle] = new scala.Array[FileHandle](relativePaths.length);
    { var i: scala.Int = 0; val n: scala.Int = relativePaths.length; while (i < n) { {
      handles(i) = this.child(relativePaths(i))
    }; i = i + 1 } }
    return handles
  }
  def list(filter: java.io.FileFilter): scala.Array[FileHandle] = {
    if (this.type$field == com.badlogic.gdx.Files.FileType.Classpath) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot list a classpath directory: " + this.file$field)
    } else ()
    val file: java.io.File = this.file()
    val relativePaths: scala.Array[java.lang.String] = file.list()
    if (relativePaths == null) {
      return new scala.Array[FileHandle](0)
    } else ()
    var handles: scala.Array[FileHandle] = new scala.Array[FileHandle](relativePaths.length)
    var count: scala.Int = 0;
    { var i: scala.Int = 0; val n: scala.Int = relativePaths.length; while (i < n) { {
      val path: java.lang.String = relativePaths(i)
      val child: FileHandle = this.child(path)
      if (!filter.accept(child.file())) {
        /* continue */ ()
      } else ()
      handles(count) = child
      count = count + 1
    }; i = i + 1 } }
    if (count < relativePaths.length) {
      val newHandles: scala.Array[FileHandle] = new scala.Array[FileHandle](count)
      java.lang.System.arraycopy(handles, 0, newHandles, 0, count)
      handles = newHandles
    } else ()
    return handles
  }
  def list(filter: java.io.FilenameFilter): scala.Array[FileHandle] = {
    if (this.type$field == com.badlogic.gdx.Files.FileType.Classpath) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot list a classpath directory: " + this.file$field)
    } else ()
    val file: java.io.File = this.file()
    val relativePaths: scala.Array[java.lang.String] = file.list()
    if (relativePaths == null) {
      return new scala.Array[FileHandle](0)
    } else ()
    var handles: scala.Array[FileHandle] = new scala.Array[FileHandle](relativePaths.length)
    var count: scala.Int = 0;
    { var i: scala.Int = 0; val n: scala.Int = relativePaths.length; while (i < n) { {
      val path: java.lang.String = relativePaths(i)
      if (!filter.accept(file, path)) {
        /* continue */ ()
      } else ()
      handles(count) = this.child(path)
      count = count + 1
    }; i = i + 1 } }
    if (count < relativePaths.length) {
      val newHandles: scala.Array[FileHandle] = new scala.Array[FileHandle](count)
      java.lang.System.arraycopy(handles, 0, newHandles, 0, count)
      handles = newHandles
    } else ()
    return handles
  }
  def list(suffix: java.lang.String): scala.Array[FileHandle] = {
    if (this.type$field == com.badlogic.gdx.Files.FileType.Classpath) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot list a classpath directory: " + this.file$field)
    } else ()
    val relativePaths: scala.Array[java.lang.String] = this.file().list()
    if (relativePaths == null) {
      return new scala.Array[FileHandle](0)
    } else ()
    var handles: scala.Array[FileHandle] = new scala.Array[FileHandle](relativePaths.length)
    var count: scala.Int = 0;
    { var i: scala.Int = 0; val n: scala.Int = relativePaths.length; while (i < n) { {
      val path: java.lang.String = relativePaths(i)
      if (!path.endsWith(suffix)) {
        /* continue */ ()
      } else ()
      handles(count) = this.child(path)
      count = count + 1
    }; i = i + 1 } }
    if (count < relativePaths.length) {
      val newHandles: scala.Array[FileHandle] = new scala.Array[FileHandle](count)
      java.lang.System.arraycopy(handles, 0, newHandles, 0, count)
      handles = newHandles
    } else ()
    return handles
  }
  def isDirectory(): scala.Boolean = {
    if (this.type$field == com.badlogic.gdx.Files.FileType.Classpath) {
      return false
    } else ()
    return this.file().isDirectory()
  }
  def child(name: java.lang.String): FileHandle = {
    if (this.file$field.getPath().length() == 0) {
      return new FileHandle(new java.io.File(name), this.type$field)
    } else ()
    return new FileHandle(new java.io.File(this.file$field, name), this.type$field)
  }
  def sibling(name: java.lang.String): FileHandle = {
    if (this.file$field.getPath().length() == 0) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot get the sibling of the root.")
    } else ()
    return new FileHandle(new java.io.File(this.file$field.getParent(), name), this.type$field)
  }
  def parent(): FileHandle = {
    var parent: java.io.File = this.file$field.getParentFile()
    if (parent == null) {
      if (this.type$field == com.badlogic.gdx.Files.FileType.Absolute) {
        parent = new java.io.File("/")
      } else {
        parent = new java.io.File("")
      }
    } else ()
    return new FileHandle(parent, this.type$field)
  }
  def mkdirs(): scala.Unit = {
    if (this.type$field == com.badlogic.gdx.Files.FileType.Classpath) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot mkdirs with a classpath file: " + this.file$field)
    } else ()
    if (this.type$field == com.badlogic.gdx.Files.FileType.Internal) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot mkdirs with an internal file: " + this.file$field)
    } else ()
    this.file().mkdirs()
  }
  def exists(): scala.Boolean = {
    this.type$field match {
      case com.badlogic.gdx.Files.FileType.Internal => {
        if (this.file().exists()) {
          return true
        } else ()
        return classOf[FileHandle].getResource("/" + this.file$field.getPath().replace('\\', '/')) != null
      }
      case com.badlogic.gdx.Files.FileType.Classpath => {
        return classOf[FileHandle].getResource("/" + this.file$field.getPath().replace('\\', '/')) != null
      }
    }
    return this.file().exists()
  }
  def delete(): scala.Boolean = {
    if (this.type$field == com.badlogic.gdx.Files.FileType.Classpath) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot delete a classpath file: " + this.file$field)
    } else ()
    if (this.type$field == com.badlogic.gdx.Files.FileType.Internal) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot delete an internal file: " + this.file$field)
    } else ()
    return this.file().delete()
  }
  def deleteDirectory(): scala.Boolean = {
    if (this.type$field == com.badlogic.gdx.Files.FileType.Classpath) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot delete a classpath file: " + this.file$field)
    } else ()
    if (this.type$field == com.badlogic.gdx.Files.FileType.Internal) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot delete an internal file: " + this.file$field)
    } else ()
    return FileHandle.deleteDirectory(this.file())
  }
  def emptyDirectory(): scala.Unit = {
    this.emptyDirectory(false)
  }
  def emptyDirectory(preserveTree: scala.Boolean): scala.Unit = {
    if (this.type$field == com.badlogic.gdx.Files.FileType.Classpath) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot delete a classpath file: " + this.file$field)
    } else ()
    if (this.type$field == com.badlogic.gdx.Files.FileType.Internal) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot delete an internal file: " + this.file$field)
    } else ()
    FileHandle.emptyDirectory(this.file(), preserveTree)
  }
  def copyTo(dest$arg: FileHandle): scala.Unit = {
    var dest: FileHandle = dest$arg
    if (!this.isDirectory()) {
      if (dest.isDirectory()) {
        dest = dest.child(this.name())
      } else ()
      FileHandle.copyFile(this, dest)
      return
    } else ()
    if (dest.exists()) {
      if (!dest.isDirectory()) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Destination exists but is not a directory: " + dest)
      } else ()
    } else {
      dest.mkdirs()
      if (!dest.isDirectory()) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Destination directory cannot be created: " + dest)
      } else ()
    }
    FileHandle.copyDirectory(this, dest.child(this.name()))
  }
  def moveTo(dest: FileHandle): scala.Unit = {
    this.type$field match {
      case com.badlogic.gdx.Files.FileType.Classpath => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot move a classpath file: " + this.file$field)
      }
      case com.badlogic.gdx.Files.FileType.Internal => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot move an internal file: " + this.file$field)
      }
      case com.badlogic.gdx.Files.FileType.Absolute | com.badlogic.gdx.Files.FileType.External => {
        if (this.file().renameTo(dest.file())) {
          return
        } else ()
      }
    }
    this.copyTo(dest)
    this.delete()
    if (this.exists() && this.isDirectory()) {
      this.deleteDirectory()
    } else ()
  }
  def length(): scala.Long = {
    if ((this.type$field == com.badlogic.gdx.Files.FileType.Classpath) || ((this.type$field == com.badlogic.gdx.Files.FileType.Internal) && (!this.file$field.exists()))) {
      val input: java.io.InputStream = this.read()
      try {
        return input.available()
      } catch {
        case ignored: java.lang.Exception => {
          ()
        }
      } finally {
        com.badlogic.gdx.utils.StreamUtils.closeQuietly(input)
      }
      return 0
    } else ()
    return this.file().length()
  }
  def lastModified(): scala.Long = {
    return this.file().lastModified()
  }
  def equals(obj: java.lang.Object): scala.Boolean = {
    if (!obj.isInstanceOf[FileHandle]) {
      return false
    } else ()
    val other: FileHandle = obj.asInstanceOf[FileHandle].asInstanceOf[FileHandle]
    return (this.type$field == other.type$field) && this.path().equals(other.path())
  }
  def hashCode(): scala.Int = {
    var hash: scala.Int = 1
    hash = (hash * 37) + this.type$field.hashCode()
    hash = (hash * 67) + this.path().hashCode()
    return hash
  }
  def toString(): java.lang.String = {
    return this.file$field.getPath().replace('\\', '/')
  }
}
object FileHandle {
  def tempFile(prefix: java.lang.String): FileHandle = {
    try {
      return new FileHandle(java.io.File.createTempFile(prefix, null))
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Unable to create temp file.", ex)
      }
    }
  }
  def tempDirectory(prefix: java.lang.String): FileHandle = {
    try {
      val file: java.io.File = java.io.File.createTempFile(prefix, null)
      if (!file.delete()) {
        throw new java.io.IOException("Unable to delete temp file: " + file)
      } else ()
      if (!file.mkdir()) {
        throw new java.io.IOException("Unable to create temp directory: " + file)
      } else ()
      return new FileHandle(file)
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Unable to create temp file.", ex)
      }
    }
  }
  private def emptyDirectory(file: java.io.File, preserveTree: scala.Boolean): scala.Unit = {
    if (file.exists()) {
      val files: scala.Array[java.io.File] = file.listFiles()
      if (files != null) {
        { var i: scala.Int = 0; val n: scala.Int = files.length; while (i < n) { {
          if (!files(i).isDirectory()) {
            files(i).delete()
          } else {
            if (preserveTree) {
              FileHandle.emptyDirectory(files(i), true)
            } else {
              FileHandle.deleteDirectory(files(i))
            }
          }
        }; i = i + 1 } }
      } else ()
    } else ()
  }
  private def deleteDirectory(file: java.io.File): scala.Boolean = {
    FileHandle.emptyDirectory(file, false)
    return file.delete()
  }
  private def copyFile(source: FileHandle, dest: FileHandle): scala.Unit = {
    try {
      dest.write(source.read(), false)
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException((((((((("Error copying source file: " + source.file$field) + " (") + source.type$field) + ")\n") + "To destination: ") + dest.file$field) + " (") + dest.type$field) + ")", ex)
      }
    }
  }
  private def copyDirectory(sourceDir: FileHandle, destDir: FileHandle): scala.Unit = {
    destDir.mkdirs()
    val files: scala.Array[FileHandle] = sourceDir.list();
    { var i: scala.Int = 0; val n: scala.Int = files.length; while (i < n) { {
      val srcFile: FileHandle = files(i)
      val destFile: FileHandle = destDir.child(srcFile.name())
      if (srcFile.isDirectory()) {
        FileHandle.copyDirectory(srcFile, destFile)
      } else {
        FileHandle.copyFile(srcFile, destFile)
      }
    }; i = i + 1 } }
  }
}