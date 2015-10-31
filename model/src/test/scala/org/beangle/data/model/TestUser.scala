package org.beangle.data.model

/**
 * @author chaostone
 */
class TestUser extends LongId {

  var member: NamedMember = _
}

class NamedMember extends Component {
  var name: Name = _
}

class Name extends Component {
  var firstName: String = _
  var lastName: String = _
}