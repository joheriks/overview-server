define [
  '../views/TagSelect'
  '../views/TagThis'
  './TagDialogController'
], (TagSelectView, TagThisView, TagDialogController) ->
  tag_list_controller = (options) ->
    openTagDialog = ->
      new TagDialogController(tags: options.tags, state: options.state)

    tagSelectView = new TagSelectView
      collection: options.tags
      state: options.state
      el: options.tagSelectEl

    documentListTagsView = new TagThisView
      tags: options.tags
      state: options.state
      keyboardController: options.keyboardController
      el: options.documentListTagsEl
      tagTarget: 'list'

    documentTagsView = new TagThisView
      tags: options.tags
      state: options.state
      keyboardController: options.keyboardController
      el: options.documentTagsEl
      tagTarget: 'document'

    tagSelectView.on('organize-clicked', openTagDialog)
    documentListTagsView.on('organize-clicked', openTagDialog)
    documentTagsView.on('organize-clicked', openTagDialog)
