define [
  'underscore'
  'jquery'
  'backbone'
  './models/TransactionQueue'
  './models/State'
  './controllers/KeyboardController'
  './controllers/ViewsController'
  './controllers/tag_list_controller'
  './controllers/document_list_controller'
  './controllers/ViewAppController'
  './controllers/TourController'
  './views/SearchView'
  './views/TransactionQueueErrorMonitor'
  './views/Mode'
  '../Tree/app'
  '../View/app'
  '../Job/app'
], (_, $, Backbone, \
    TransactionQueue, State, \
    KeyboardController, \
    ViewsController, tag_list_controller, document_list_controller, \
    ViewAppController, \
    TourController, \
    SearchView, \
    TransactionQueueErrorMonitor, \
    ModeView, \
    TreeApp, ViewApp, JobApp) ->

  class App
    constructor: (options) ->
      throw 'need options.mainEl' if !options.mainEl

      @el = options.mainEl

      @tourEnabled = @el.getAttribute('data-tooltips-enabled') == 'true'

      @transactionQueue = @_initializeTransactionQueue()
      documentSetId = window.location.pathname.split('/')[2]
      @state = new State({}, documentSetId: documentSetId, transactionQueue: @transactionQueue)
      @state.once('sync', => @_initializeUi())
      @state.init()

    _listenForRefocus: ->
      refocus = ->
        # Pull focus out of the iframe.
        #
        # We can't listen for events on the document iframe; so if it's present,
        # it breaks keyboard shortcuts. We need to re-grab focus whenever we can
        # without disturbing the user.
        #
        # For instance, if the user is logging in to DocumentCloud in the iframe,
        # we don't want to steal focus; so a timer is bad, and a mousemove handler
        # is bad. But if we register a click, it's worth using that to steal focus.
        window.focus() if document.activeElement?.tagName == 'IFRAME'

      refocus_body_on_leave_window = ->
        # Ugly fix for https://github.com/overview/overview-server/issues/321
        hidden = undefined

        callback = (e) ->
          if !document[hidden]
            refocus()

        if document[hidden]?
          document.addEventListener("visibilitychange", callback)
        else if document[hidden = "mozHidden"]?
          document.addEventListener("mozvisibilitychange", callback)
        else if document[hidden = "webkitHidden"]?
          document.addEventListener("webkitvisibilitychange", callback)
        else if document[hidden = "msHidden"]?
          document.addEventListener("msvisibilitychange", callback)
        else
          hidden = undefined

      refocus_body_on_event = ->
        # Ugly fix for https://github.com/overview/overview-server/issues/362
        $('body').on 'click', (e) ->
          refocus()

      refocus_body_on_leave_window()
      refocus_body_on_event()
      undefined

    _listenForResize: (documentEl) ->
      $documentEl = $(documentEl)

      refreshWidth = ->
        # Round the iframe's parent's width, because it needs an integer number of px
        $documentEl.find('iframe')
          .width(1)
          .width($documentEl.width())

      throttledRefreshWidth = _.throttle(refreshWidth, 100)

      $(window).resize(throttledRefreshWidth)

      refreshWidth()

    _buildHtml: ->
      html = """
        <div id="view-pane">
          <div id="search-and-tags">
            <div id="select-search"></div>
            <div id="select-tags"></div>
          </div>
          <div id="views"></div>
          <div id="view"></div>
        </div>
        <div id="document-list-pane">
          <div id="document-list-title-row">
            <div id="document-list-title"></div>
            <div id="document-list-tags"></div>
          </div>
          <div id="document-list"></div>
        </div>
        <div id="document-pane">
          <div id="document-title-row">
            <div id="document-title"></div>
            <div id="document-tags"></div>
          </div>
          <div id="document"></div>
        </div>
        <div id="transaction-queue-error-monitor"></div>
      """

      $(@el).html(html)

      el = (id) -> document.getElementById(id)

      main: @el
      views: el('views')
      view: el('view')
      selectTag: el('select-tags')
      selectSearch: el('select-search')
      documentList: el('document-list')
      documentListTitle: el('document-list-title')
      documentListTags: el('document-list-tags')
      document: el('document')
      documentTags: el('document-tags')
      transactionQueueErrorMonitor: el('transaction-queue-error-monitor')

    _initializeTransactionQueue: ->
      transactionQueue = new TransactionQueue()

      # Override Backbone.ajax so all Backbone operations use transactionQueue
      #
      # XXX This means collection.fetch()`.done()` and `.fail()` will not work:
      # we use real Promise objects, not jQuery ones. You can use `success:`
      # and `error:` callbacks, or use the two-argument `.then()`.
      Backbone.ajax = (args...) -> transactionQueue.ajax(args...)

      transactionQueue

    _initializeUi: ->
      @state.views.pollUntilStable()

      els = @_buildHtml()
      keyboardController = new KeyboardController(document)

      @state.on 'change:view', (__, view) =>
        return if !view?
        # Change URL so a page refresh brings us to this view
        url = "/documentsets/#{@state.documentSetId}/#{view.id}"
        window.history?.replaceState(url, '', url)

      controller = new ViewsController(@state.views, @state)
      els.views.appendChild(controller.el)

      new ModeView(el: @el, state: @state)
      new SearchView(el: els.selectSearch, state: @state)

      @_listenForRefocus()
      @_listenForResize(els.document)

      tag_list_controller
        state: @state
        tags: @state.tags
        tagSelectEl: els.selectTag
        documentListTagsEl: els.documentListTags
        documentTagsEl: els.documentTags
        keyboardController: keyboardController

      document_list_controller(els.documentListTitle, els.documentList, els.document, @state, keyboardController)

      new ViewAppController
        el: els.view
        state: @state
        transactionQueue: @transactionQueue
        keyboardController: keyboardController
        viewAppConstructors:
          tree: TreeApp
          view: ViewApp
          job: JobApp
          error: JobApp

      new TransactionQueueErrorMonitor
        model: @transactionQueue
        el: els.transactionQueueErrorMonitor

      if @tourEnabled
        TourController()
