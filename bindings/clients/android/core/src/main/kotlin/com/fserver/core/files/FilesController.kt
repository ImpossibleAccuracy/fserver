package com.fserver.core.files

import com.fserver.common.task.map
import com.fserver.core.files.scan.toCore
import com.fserver.core.files.scan.toFiles
import com.fserver.files.FilesNode

class FilesController internal constructor(
    private val node: FilesNode,
) {
    fun loadContent(directory: SourceLocation) = node.scanner
        .scan(directory = directory.toFiles())
        .map(
            progressMapper = { it.toCore() },
            resultMapper = { list ->
                list.map { it.toCore() }
            },
        )
}
