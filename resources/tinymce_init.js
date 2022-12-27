const image_upload_handler = (blobInfo, progress) => new Promise((resolve, reject) => {
  const xhr = new XMLHttpRequest();
  xhr.withCredentials = false;
  xhr.open('POST', '/app/images/upload');
  xhr.setRequestHeader('X-CSRF-TOKEN', document.querySelector('input[name="__anti-forgery-token"]').value);

  xhr.upload.onprogress = (e) => {
    progress(e.loaded / e.total * 100);
  };

  xhr.onload = () => {
    if (xhr.status === 403) {
      reject({ message: 'HTTP Error: ' + xhr.status, remove: true });
      return;
    }

    if (xhr.status < 200 || xhr.status >= 300) {
      reject('HTTP Error: ' + xhr.status);
      return;
    }

    const json = JSON.parse(xhr.responseText);

    if (!json || typeof json.location != 'string') {
      reject('Invalid JSON: ' + xhr.responseText);
      return;
    }

    resolve(json.location);
  };

  xhr.onerror = () => {
    reject('Image upload failed due to a XHR Transport error. Code: ' + xhr.status);
  };

  const formData = new FormData();
  formData.append('file', blobInfo.blob(), blobInfo.filename());

  xhr.send(formData);
});

tinymce.IconManager.add('customIcons', {
  icons: {
    'callout': '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24"><g><path d="M20 7a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h16zm0 2H4v6h16V9z"/><path d="M7 10.5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3z"/></g></svg>',
    'swatch-blue': '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24"><rect width="18" height="18" x="3" y="3" fill="#76B8E4" fill-rule="evenodd" rx="2"/></svg>',
    'swatch-yellow': '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24"><rect width="18" height="18" x="3" y="3" fill="#FAD281" fill-rule="evenodd" rx="2"/></svg>',
    'swatch-red': '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24"><rect width="18" height="18" x="3" y="3" fill="#FF8686" fill-rule="evenodd" rx="2"/></svg>',
  }
});
tinymce.init({ selector: '#content',
  plugins: 'code autosave lists anchor link autolink codesample emoticons fullscreen nonbreaking preview wordcount image noneditable',
  //toolbar: 'code',
  images_upload_handler: image_upload_handler,
  toolbar_sticky: true,
  height: '100vh',
  menu: {
    file: { title: 'File', items: 'restoredraft' },
    edit: { title: 'Edit', items: 'undo redo | cut copy paste pastetext | selectall | searchreplace' },
    view: { title: 'View', items: 'code | visualaid visualchars visualblocks | spellchecker | preview fullscreen | showcomments' },
    insert: { title: 'Insert', items: 'image link callout media addcomment pageembed template codesample inserttable | charmap emoticons hr | pagebreak nonbreaking anchor tableofcontents | insertdatetime' },
    format: { title: 'Format', items: 'bold italic underline strikethrough superscript subscript codeformat | styles blocks fontfamily fontsize align lineheight | forecolor backcolor | language | removeformat' },
    tools: { title: 'Tools', items: 'spellchecker spellcheckerlanguage | a11ycheck code wordcount' },
    table: { title: 'Table', items: 'inserttable | cell row column | advtablesort | tableprops deletetable' },
    help: { title: 'Help', items: 'help' }
  },
  toolbar: 'undo redo styles image link bold italic numlist bullist alignleft aligncenter alignright alignjustify outdent indent code callout',
  codesample_languages: [
    { text: 'Clojure', value: 'clojure' },
    { text: 'HTML/XML', value: 'markup' },
    { text: 'JavaScript', value: 'javascript' },
    { text: 'CSS', value: 'css' },
    { text: 'PHP', value: 'php' },
    { text: 'Ruby', value: 'ruby' },
    { text: 'Python', value: 'python' },
    { text: 'Java', value: 'java' },
    { text: 'C', value: 'c' },
    { text: 'C#', value: 'csharp' },
    { text: 'C++', value: 'cpp' }
  ],
  codesample_global_prismjs: true,
  skin: (darkModeOn ? 'oxide-dark' : 'oxide'),
  content_css: [
    '/css/tinymce.css',
    (darkModeOn ? 'dark' : 'default'),
    (darkModeOn ? '/css/tinymce-dark.css' : '/css//tinymce-light.css')
  ],

  // callouts
  noneditable_noneditable_class: 'callout',
  noneditable_editable_class: 'content',
  contextmenu: false,
  icons: 'customIcons',

  setup: (editor) => {
    const variants = ['yellow', 'red', 'blue'];

    editor.ui.registry.addButton('callout', {
      icon: 'callout',
      tooltip: 'Insert callout',
      onAction: function () {
        tinymce.activeEditor.insertContent(`
                            <div class="callout">
                                <div class="content"><p>Callout</p></div>
                            </div>
                            <p>&nbsp;</p>
                        `);
      }
    });

    variants.forEach(variant => {
      editor.ui.registry.addButton(`callout${variant}`, {
        icon: `swatch-${variant}`,
        tooltip: `${variant} callout`,
        onAction: function () {
          const node = tinymce.activeEditor.selection.getNode();
          const callout = tinymce.activeEditor.dom.getParent(node, '.callout');
          tinymce.DOM.removeClass(callout, 'red');
          tinymce.DOM.removeClass(callout, 'yellow');
          tinymce.DOM.addClass(callout, variant);
        }
      });
    });

    editor.ui.registry.addButton('removecallout', {
      icon: 'remove',
      tooltip: 'Remove callout',
      onAction: function () {
        const node = tinymce.activeEditor.selection.getNode();
        const callout = tinymce.activeEditor.dom.getParent(node, '.callout');
        tinymce.activeEditor.selection.select(callout);
        tinymce.activeEditor.execCommand('delete');
      }
    });

    editor.ui.registry.addContextToolbar('callout', {
      predicate: function (node) {
        return node.classList.contains('callout');
      },
      items: 'calloutblue calloutyellow calloutred | removecallout',
      position: 'node',
      scope: 'node'
    });

    editor.ui.registry.addNestedMenuItem('callout', {
      text: 'Callout',
      icon: 'callout',
      getSubmenuItems: () => {
        return variants.map(variant => {
          return {
            type: 'menuitem',
            text: variant,
            icon: `swatch-${variant}`,
            onAction: () => {
              tinymce.activeEditor.insertContent(`
                                        <div class="callout">
                                            <div class="content"><p>Callout</p></div>
                                        </div>
                                    `);
            }
          };
        });
      }
    });
  },

});
