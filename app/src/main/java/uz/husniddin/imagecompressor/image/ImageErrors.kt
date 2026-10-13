package uz.husniddin.imagecompressor.image

/** Failures that carry a message suitable for showing to the user. */
open class ImageProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ImageReadException(message: String, cause: Throwable? = null) : ImageProcessingException(message, cause)

class UnsupportedImageException(message: String) : ImageProcessingException(message)

class ImageWriteException(message: String, cause: Throwable? = null) : ImageProcessingException(message, cause)
