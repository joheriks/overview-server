Locator = require('./Locator')
debug = require('debug')('Element')

# A wrapper around a Promise of a Selenium WebElement.
module.exports = class Element
  constructor: (@driverElement) ->

  # Schedules a click of the element. Returns an empty Promise.
  click: ->
    debug('click')
    @driverElement.click()
      .thenCatch((ex) -> console.warn('Failed click', ex); throw ex)

  # Schedules sending keys to the element. Returns an empty Promise.
  sendKeys: (keys) ->
    debug("sendKeys(#{keys})")
    @driverElement.sendKeys(keys)
      .thenCatch((ex) -> console.warn('Failed sendKeys', ex); throw ex)

  # Returns a Promise of the text in the element.
  getText: ->
    debug('getText()')
    @driverElement.getText()
      .thenCatch((ex) -> console.warn('Failed getText', ex); throw ex)
