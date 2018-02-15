/* /////////////////////////////////////////////////////////////////////////////////////////////////
 //                          Copyright (c) Tapkey GmbH
 //
 //         All rights are reserved. Reproduction in whole or in part is
 //        prohibited without the written consent of the copyright owner.
 //    Tapkey reserves the right to make changes without notice at any time.
 //   Tapkey makes no warranty, expressed, implied or statutory, including but
 //   not limited to any implied warranty of merchantability or fitness for any
 //  particular purpose, or that the use will not infringe any third party patent,
 //   copyright or trademark. Tapkey must not be liable for any loss or damage
 //                            arising from its use.
 ///////////////////////////////////////////////////////////////////////////////////////////////// */


import UIKit

class ViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()
        // Do any additional setup after loading the view, typically from a nib.
        
        /*
        let next:SignInViewController = SignInViewController()
        
        self.present(next, animated: false) { 
         
            NSLog("Something happenend")
            
        }*/
        
        //self.presentViewController(next, animated: true, completion: nil)
        
       // self.navigationController!.pushViewController(self.storyboard!.instantiateViewController(withIdentifier: "SignInViewController") as UIViewController, animated: false)
       // self.dismiss(animated: false)
        

        
        NSLog("Something happenend")

        

        
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        //let storyBoard : UIStoryboard = UIStoryboard(name: "Main", bundle:nil)
        
      //  let nextViewController = storyBoard.instantiateViewController(withIdentifier: "SignInViewController");
      //  self.present(nextViewController, animated:true, completion:nil)
    }

    override func didReceiveMemoryWarning() {
        super.didReceiveMemoryWarning()
        // Dispose of any resources that can be recreated.
    }


}

