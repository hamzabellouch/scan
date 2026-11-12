package com.tkno.scan

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tkno.scan.docscan.*
import com.tkno.scan.ui.component.ConfirmButton
import com.tkno.scan.ui.component.DismissButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var documents by remember { mutableStateOf<List<ScannedDocumentItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Display & filter state
    var viewMode by remember { mutableStateOf(ViewMode.List) }
    var sortOrder by remember { mutableStateOf(SortOrder.NewestFirst) }
    var selectedFilter by remember { mutableStateOf(DocTypeFilter.All) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Multi-selection state
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItemIds = remember { mutableStateListOf<String>() }

    fun toggleDocumentSelection(itemId: String) {
        if (itemId in selectedItemIds) {
            selectedItemIds.remove(itemId)
            if (selectedItemIds.isEmpty()) isSelectionMode = false
        } else {
            isSelectionMode = true
            selectedItemIds.add(itemId)
        }
    }

    // Menus & Dialogs
    var isTopMenuExpanded by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<ScannedDocumentItem?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var itemsToDelete by remember { mutableStateOf<List<ScannedDocumentItem>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var previewTxtItem by remember { mutableStateOf<ScannedDocumentItem?>(null) }
    var previewTxtContent by remember { mutableStateOf<String?>(null) }

    fun refreshDocuments() {
        isLoading = true
        coroutineScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                DocLibraryManager.loadScannedDocuments(context)
            }
            documents = loaded
            isLoading = false
        }
    }

    fun openLibraryItem(item: ScannedDocumentItem) {
        when (DocLibraryManager.openDocument(context, item)) {
            OpenDocumentResult.OpenedExternally -> Unit
            OpenDocumentResult.NoTxtViewer -> {
                coroutineScope.launch {
                    val textResult = withContext(Dispatchers.IO) {
                        DocLibraryManager.readTextContent(context, item)
                    }
                    textResult.onSuccess { text ->
                        previewTxtItem = item
                        previewTxtContent = text
                    }.onFailure {
                        Toast.makeText(context, R.string.txt_preview_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            OpenDocumentResult.NoViewer, OpenDocumentResult.Failed -> {
                Toast.makeText(context, R.string.no_app_to_open_file, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshDocuments()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var hasSkippedInitialResume by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (hasSkippedInitialResume) {
                    refreshDocuments()
                } else {
                    hasSkippedInitialResume = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = isSelectionMode || isSearchActive) {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedItemIds.clear()
        } else if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        }
    }

    // Filter and sort documents
    val filteredDocuments = remember(documents, selectedFilter, searchQuery, sortOrder) {
        var list = documents

        // Type filter
        if (selectedFilter != DocTypeFilter.All) {
            list = list.filter { DocLibraryManager.matchesFilter(it.fileType, selectedFilter) }
        }

        // Search query
        if (searchQuery.isNotBlank()) {
            list = list.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }

        // Sort order
        when (sortOrder) {
            SortOrder.NewestFirst -> list.sortedByDescending { it.lastModified }
            SortOrder.OldestFirst -> list.sortedBy { it.lastModified }
            SortOrder.LargestFirst -> list.sortedByDescending { it.sizeBytes }
            SortOrder.SmallestFirst -> list.sortedBy { it.sizeBytes }
            SortOrder.NameAsc -> list.sortedBy { it.name.lowercase() }
            SortOrder.NameDesc -> list.sortedByDescending { it.name.lowercase() }
        }
    }

    // Group filtered documents by date group
    val groupedDocuments = remember(filteredDocuments) {
        filteredDocuments.groupBy { it.dateGroupKey }
    }

    val selectedCount = selectedItemIds.size
    val totalSelectedBytes = remember(selectedItemIds, documents) {
        documents.filter { it.id in selectedItemIds }.sumOf { it.sizeBytes }
    }

    // Rename Dialog
    if (itemToRename != null) {
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text(text = stringResource(R.string.rename_document)) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.file_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                ConfirmButton(
                    text = stringResource(R.string.rename),
                    enabled = renameInput.isNotBlank(),
                    onClick = {
                        val item = itemToRename ?: return@ConfirmButton
                        if (renameInput.isNotBlank()) {
                            DocLibraryManager.renameDocument(context, item, renameInput.trim())
                            itemToRename = null
                            refreshDocuments()
                        }
                    }
                )
            },
            dismissButton = {
                DismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { itemToRename = null }
                )
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog && itemsToDelete.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                itemsToDelete = emptyList()
            },
            title = { Text(text = stringResource(R.string.delete_page_title)) },
            text = { Text(text = stringResource(R.string.delete_docs_confirm)) },
            confirmButton = {
                ConfirmButton(
                    text = stringResource(R.string.delete_item),
                    icon = Icons.Outlined.Delete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = {
                        DocLibraryManager.deleteDocuments(context, itemsToDelete)
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedItemIds.clear()
                        }
                        showDeleteDialog = false
                        itemsToDelete = emptyList()
                        refreshDocuments()
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                    }
                )
            },
            dismissButton = {
                DismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = {
                        showDeleteDialog = false
                        itemsToDelete = emptyList()
                    }
                )
            }
        )
    }

    // Fallback In-App TXT Viewer Dialog (used when no external text viewer app handles text/plain)
    val activeTxtItem = previewTxtItem
    val activeTxtContent = previewTxtContent
    if (activeTxtItem != null && activeTxtContent != null) {
        AlertDialog(
            onDismissRequest = {
                previewTxtItem = null
                previewTxtContent = null
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = activeTxtItem.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText(activeTxtItem.name, activeTxtContent))
                            Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.copy)
                        )
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = activeTxtContent.ifEmpty { stringResource(R.string.txt_empty_file) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                ConfirmButton(
                    text = stringResource(R.string.close),
                    icon = null,
                    onClick = {
                        previewTxtItem = null
                        previewTxtContent = null
                    }
                )
            },
            dismissButton = {
                DismissButton(
                    text = stringResource(R.string.share),
                    icon = Icons.Outlined.Share,
                    onClick = {
                        DocLibraryManager.shareDocuments(context, listOf(activeTxtItem))
                    }
                )
            }
        )
    }

    // "Sort by" Bottom Sheet (matching Image 5)
    if (showSortBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortBottomSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.sort_by),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val sortOptions = listOf(
                    SortOrder.NewestFirst to stringResource(R.string.newest_first),
                    SortOrder.OldestFirst to stringResource(R.string.oldest_first),
                    SortOrder.LargestFirst to stringResource(R.string.largest_first),
                    SortOrder.SmallestFirst to stringResource(R.string.smallest_first),
                    SortOrder.NameAsc to stringResource(R.string.name_a_z),
                    SortOrder.NameDesc to stringResource(R.string.name_z_a)
                )

                sortOptions.forEach { (order, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortOrder = order
                                showSortBottomSheet = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        RadioButton(
                            selected = sortOrder == order,
                            onClick = {
                                sortOrder = order
                                showSortBottomSheet = false
                            }
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // Multi-Selection Top Bar (matching Image 1)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                isSelectionMode = false
                                selectedItemIds.clear()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close selection",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.selected_count_format, selectedCount, formatFileSize(totalSelectedBytes)),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row {
                        // Share Selected
                        IconButton(
                            onClick = {
                                val selectedDocs = documents.filter { it.id in selectedItemIds }
                                DocLibraryManager.shareDocuments(context, selectedDocs)
                            },
                            enabled = selectedCount > 0
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Delete Selected
                        IconButton(
                            onClick = {
                                val selectedDocs = documents.filter { it.id in selectedItemIds }
                                itemsToDelete = selectedDocs
                                showDeleteDialog = true
                            },
                            enabled = selectedCount > 0
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                // Regular Top Bar (matching Images 2 & 3)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.library),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Search toggle
                            IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // View mode toggle (List <-> Grid)
                            IconButton(
                                onClick = {
                                    viewMode = if (viewMode == ViewMode.List) ViewMode.Grid else ViewMode.List
                                }
                            ) {
                                Icon(
                                    imageVector = if (viewMode == ViewMode.List) Icons.Outlined.GridView else Icons.Outlined.ViewList,
                                    contentDescription = "Toggle view",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // 3-dots menu
                            Box {
                                IconButton(onClick = { isTopMenuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Menu",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                DropdownMenu(
                                    expanded = isTopMenuExpanded,
                                    onDismissRequest = { isTopMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(R.string.select_all)) },
                                        onClick = {
                                            isTopMenuExpanded = false
                                            isSelectionMode = true
                                            selectedItemIds.clear()
                                            selectedItemIds.addAll(filteredDocuments.map { it.id })
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(R.string.sort_by)) },
                                        onClick = {
                                            isTopMenuExpanded = false
                                            showSortBottomSheet = true
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Optional expandable search bar
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(text = stringResource(R.string.search_documents)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    // Filter Chips Row (All, PDF, Images, Word, TXT)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFilter == DocTypeFilter.All,
                            onClick = { selectedFilter = DocTypeFilter.All },
                            label = { Text(text = stringResource(R.string.all)) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        FilterChip(
                            selected = selectedFilter == DocTypeFilter.PDF,
                            onClick = { selectedFilter = DocTypeFilter.PDF },
                            label = { Text(text = stringResource(R.string.pdf)) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        FilterChip(
                            selected = selectedFilter == DocTypeFilter.Images,
                            onClick = { selectedFilter = DocTypeFilter.Images },
                            label = { Text(text = stringResource(R.string.filter_images)) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        FilterChip(
                            selected = selectedFilter == DocTypeFilter.Word,
                            onClick = { selectedFilter = DocTypeFilter.Word },
                            label = { Text(text = stringResource(R.string.filter_word)) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        FilterChip(
                            selected = selectedFilter == DocTypeFilter.Txt,
                            onClick = { selectedFilter = DocTypeFilter.Txt },
                            label = { Text(text = stringResource(R.string.filter_txt)) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .padding(bottom = 80.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (filteredDocuments.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_library_files_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.no_library_files_desc),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                if (viewMode == ViewMode.List) {
                    // List View Mode (matching Image 2)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        groupedDocuments.forEach { (dateGroup, itemsInGroup) ->
                            val allGroupSelected = itemsInGroup.all { it.id in selectedItemIds }

                            // Date Group Header
                            item(key = "header_$dateGroup") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateGroup,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    // Section selection checkmark icon
                                    IconButton(
                                        onClick = {
                                            if (allGroupSelected) {
                                                selectedItemIds.removeAll(itemsInGroup.map { it.id })
                                                if (selectedItemIds.isEmpty()) isSelectionMode = false
                                            } else {
                                                isSelectionMode = true
                                                itemsInGroup.forEach {
                                                    if (it.id !in selectedItemIds) selectedItemIds.add(it.id)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (allGroupSelected) Icons.Default.CheckCircle else Icons.Outlined.CheckCircleOutline,
                                            contentDescription = "Select date section",
                                            tint = if (allGroupSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Items in this date group
                            items(itemsInGroup, key = { it.id }) { item ->
                                val isSelected = item.id in selectedItemIds
                                LibraryListItem(
                                    item = item,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) {
                                            toggleDocumentSelection(item.id)
                                        } else {
                                            openLibraryItem(item)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        toggleDocumentSelection(item.id)
                                    },
                                    onRename = {
                                        itemToRename = item
                                        val dotIdx = item.name.lastIndexOf('.')
                                        renameInput = if (dotIdx > 0) item.name.substring(0, dotIdx) else item.name
                                    },
                                    onShare = {
                                        DocLibraryManager.shareDocuments(context, listOf(item))
                                    },
                                    onDelete = {
                                        itemsToDelete = listOf(item)
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Grid View Mode (matching Image 3)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        groupedDocuments.forEach { (dateGroup, itemsInGroup) ->
                            val allGroupSelected = itemsInGroup.all { it.id in selectedItemIds }

                            item(span = { GridItemSpan(3) }, key = "grid_header_$dateGroup") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateGroup,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    IconButton(
                                        onClick = {
                                            if (allGroupSelected) {
                                                selectedItemIds.removeAll(itemsInGroup.map { it.id })
                                                if (selectedItemIds.isEmpty()) isSelectionMode = false
                                            } else {
                                                isSelectionMode = true
                                                itemsInGroup.forEach {
                                                    if (it.id !in selectedItemIds) selectedItemIds.add(it.id)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (allGroupSelected) Icons.Default.CheckCircle else Icons.Outlined.CheckCircleOutline,
                                            contentDescription = "Select section",
                                            tint = if (allGroupSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            items(itemsInGroup, key = { "grid_${it.id}" }) { item ->
                                val isSelected = item.id in selectedItemIds
                                LibraryGridItem(
                                    item = item,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) {
                                            toggleDocumentSelection(item.id)
                                        } else {
                                            openLibraryItem(item)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        toggleDocumentSelection(item.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryListItem(
    item: ScannedDocumentItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Card with selection badge (matching Image 1 & 2)
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    width = if (isSelected) 2.dp else 0.5.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            val thumbFile = item.thumbnailFile
            if (thumbFile != null && thumbFile.exists()) {
                val bitmap = remember(thumbFile.absolutePath) {
                    BitmapFactory.decodeFile(thumbFile.absolutePath)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    DefaultFileIcon(item)
                }
            } else {
                DefaultFileIcon(item)
            }

            // Selection Checkmark Badge in top-left corner (matching Image 1)
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4285F4)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Document Details (Name & Subtitle)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${item.formattedSize} • ${item.relativeTime}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 3-dots Item Context Menu
        if (!isSelectionMode) {
            Box {
                IconButton(onClick = { isMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.open)) },
                        leadingIcon = { Icon(Icons.Outlined.OpenInNew, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.share)) },
                        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.rename)) },
                        leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.delete_item), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            isMenuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryGridItem(
    item: ScannedDocumentItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = if (isSelected) 2.5.dp else 0.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        val thumbFile = item.thumbnailFile
        if (thumbFile != null && thumbFile.exists()) {
            val bitmap = remember(thumbFile.absolutePath) {
                BitmapFactory.decodeFile(thumbFile.absolutePath)
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                DefaultFileIcon(item)
            }
        } else {
            DefaultFileIcon(item)
        }

        // Top-right file size overlay (matching Image 3)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = item.formattedSize,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }

        // Selection Checkmark Badge in top-left corner (matching Image 1)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4285F4)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Bottom file name banner (matching Image 3)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = item.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DefaultFileIcon(item: ScannedDocumentItem) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (item.fileType) {
            LibraryFileType.PDF -> {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    tint = Color(0xFFEA4335),
                    modifier = Modifier.size(36.dp)
                )
            }
            LibraryFileType.Image -> {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            LibraryFileType.Word -> {
                val wordTint = Color(0xFF4285F4)
                val badge = item.name.substringAfterLast('.', "WORD").uppercase(Locale.US)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = wordTint,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = wordTint
                    )
                }
            }
            LibraryFileType.Txt -> {
                val txtTint = Color(0xFF34A853)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = txtTint,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "TXT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = txtTint
                    )
                }
            }
        }
    }
}
